package at.nimmdas.app.ui.screens.messages

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import at.nimmdas.app.data.model.Message
import at.nimmdas.app.data.model.MessageDocument
import at.nimmdas.app.data.model.MessageLocation
import at.nimmdas.app.data.model.OfferResponseRequest
import at.nimmdas.app.data.model.SendMessageRequest
import at.nimmdas.app.data.model.StatusUpdateRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private var pollingJob: Job? = null
    private val apiClient = (app as NimmdasApp).apiClient
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private var _partnerId: String = ""
    val partnerId: String get() = _partnerId
    private var _listingId: String = ""
    val listingId: String get() = _listingId
    var currentUserId: String = ""
        private set

    // Thread metadata
    private val _partnerName = MutableStateFlow("")
    val partnerName = _partnerName.asStateFlow()
    private val _partnerAvatar = MutableStateFlow<String?>(null)
    val partnerAvatar = _partnerAvatar.asStateFlow()
    private val _listingTitle = MutableStateFlow("")
    val listingTitle = _listingTitle.asStateFlow()
    private val _listingImage = MutableStateFlow<String?>(null)
    val listingImage = _listingImage.asStateFlow()
    
    private val _isSeller = MutableStateFlow(false)
    val isSeller = _isSeller.asStateFlow()
    private val _listingStatus = MutableStateFlow("active")
    val listingStatus = _listingStatus.asStateFlow()

    fun loadThread(threadId: String) {
        val parts = threadId.split("_", limit = 2)
        if (parts.size < 2) return
        _partnerId = parts[0]
        _listingId = parts[1]

        viewModelScope.launch {
            currentUserId = apiClient.getUserId() ?: ""
            _isLoading.value = true
            try {
                // Load thread info from threads list
                val threadsResp = apiClient.api.getThreads()
                var threadFound = false
                if (threadsResp.isSuccessful) {
                    val thread = threadsResp.body()?.find {
                        it.partnerId == _partnerId && it.listingId == _listingId
                    }
                    if (thread != null) {
                        threadFound = true
                        _partnerName.value = thread.partnerName ?: "Unbekannt"
                        _partnerAvatar.value = thread.partnerAvatar
                        _listingTitle.value = thread.listingTitle ?: ""
                        _listingImage.value = thread.listingImage
                    }
                }
                
                // Fallback for new chats — the thread list has no entry yet
                if (!threadFound) {
                    try {
                        val userResp = apiClient.api.getUserProfile(_partnerId)
                        if (userResp.isSuccessful) {
                            val user = userResp.body()?.user
                            if (user != null) {
                                _partnerName.value = user.name
                                _partnerAvatar.value = user.avatar
                            }
                        }
                    } catch (_: Exception) {}
                }

                // The thread list carries no seller/status info, so always load the listing.
                // Doing this only for new chats left the seller menu ("Verkauft"/"Reserviert")
                // hidden in every existing conversation.
                try {
                    val listingResp = apiClient.api.getListingById(_listingId)
                    if (listingResp.isSuccessful) {
                        val listing = listingResp.body()
                        if (listing != null) {
                            if (_listingTitle.value.isBlank()) _listingTitle.value = listing.title ?: ""
                            if (_listingImage.value == null) _listingImage.value = listing.images?.firstOrNull()
                            _listingStatus.value = listing.status ?: "active"
                            _isSeller.value = listing.sellerId?.id == currentUserId
                        }
                    }
                } catch (_: Exception) {}

                // Load messages
                val response = apiClient.api.getThread(_partnerId, _listingId)
                if (response.isSuccessful) {
                    _messages.value = response.body() ?: emptyList()
                }
                // Start polling for new messages
                startPolling()
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                try {
                    val response = apiClient.api.getThread(_partnerId, _listingId)
                    if (response.isSuccessful) {
                        _messages.value = response.body() ?: emptyList()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private suspend fun refresh() {
        val response = apiClient.api.getThread(_partnerId, _listingId)
        if (response.isSuccessful) _messages.value = response.body() ?: emptyList()
    }

    fun sendMessage(
        content: String,
        type: String = "text",
        priceOffer: Double? = null,
        image: String? = null,
        document: MessageDocument? = null,
        location: MessageLocation? = null,
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _isSending.value = true
            try {
                val res = apiClient.api.sendMessage(SendMessageRequest(
                    content = content,
                    receiverId = _partnerId,
                    listingId = _listingId,
                    messageType = type,
                    priceOffer = priceOffer,
                    image = image,
                    document = document,
                    location = location,
                ))
                if (!res.isSuccessful) {
                    // Spam filter and rate limits answer with a readable reason.
                    _error.value = when (res.code()) {
                        403 -> "Nachricht wurde blockiert (Spam-Schutz)."
                        429 -> "Zu viele neue Chats – bitte später erneut versuchen."
                        else -> "Senden fehlgeschlagen."
                    }
                }
                delay(300)
                refresh()
            } catch (_: Exception) {
                _error.value = "Keine Verbindung."
            }
            _isSending.value = false
        }
    }

    fun sendQuickReply(text: String) = sendMessage(text)

    /** Uploads an image or PDF, then posts it as its own message. */
    fun sendAttachment(context: android.content.Context, uri: android.net.Uri, isImage: Boolean) {
        viewModelScope.launch {
            _isSending.value = true
            try {
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    _error.value = "Datei konnte nicht gelesen werden."
                } else {
                    val mime = resolver.getType(uri) ?: if (isImage) "image/jpeg" else "application/pdf"
                    val name = queryDisplayName(context, uri) ?: if (isImage) "bild.jpg" else "dokument.pdf"
                    val body = okhttp3.RequestBody.create(mime.toMediaTypeOrNull(), bytes)
                    val part = okhttp3.MultipartBody.Part.createFormData("file", name, body)
                    val up = apiClient.api.uploadChatFile(part)
                    val data = up.body()
                    if (!up.isSuccessful || data?.url.isNullOrBlank()) {
                        _error.value = if (up.code() == 413 || up.code() == 400) "Datei zu groß oder Format nicht erlaubt." else "Upload fehlgeschlagen."
                    } else if (isImage) {
                        sendMessage("📷 Bild", type = "image", image = data!!.url)
                        return@launch
                    } else {
                        sendMessage(
                            "📎 ${data!!.name ?: name}", type = "document",
                            document = MessageDocument(
                                url = data.url!!, name = data.name ?: name,
                                size = data.size ?: bytes.size.toLong(),
                                mimeType = data.mimeType ?: mime,
                            ),
                        )
                        return@launch
                    }
                }
            } catch (_: Exception) {
                _error.value = "Upload fehlgeschlagen."
            }
            _isSending.value = false
        }
    }

    private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    /** Accepts or declines a received price offer or coin redemption. */
    fun respondToOffer(messageId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.respondToOffer(
                    OfferResponseRequest(messageId = messageId, action = if (accept) "accept" else "decline")
                )
                if (!res.isSuccessful) _error.value = "Antwort auf das Angebot fehlgeschlagen."
                refresh()
            } catch (_: Exception) {
                _error.value = "Keine Verbindung."
            }
        }
    }
    
    fun setListingStatus(status: String) {
        viewModelScope.launch {
            try {
                val resp = apiClient.api.updateListingStatus(_listingId, StatusUpdateRequest(status))
                if (resp.isSuccessful) {
                    _listingStatus.value = status
                    sendMessage("Ich habe das Inserat als $status markiert.", "auto")
                }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: String,
    onBack: () -> Unit,
    onListingClick: ((String) -> Unit)? = null,
    onCallRequested: ((String, String, String?, String?) -> Unit)? = null,
    /** Opens a PDF attachment in the in-app viewer (url, title). */
    onOpenDocument: ((String, String) -> Unit)? = null,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val partnerName by viewModel.partnerName.collectAsState()
    val partnerAvatar by viewModel.partnerAvatar.collectAsState()
    val listingTitle by viewModel.listingTitle.collectAsState()
    val listingImage by viewModel.listingImage.collectAsState()
    val isSeller by viewModel.isSeller.collectAsState()
    val listingStatus by viewModel.listingStatus.collectAsState()
    var showQuickReplies by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showPriceOffer by remember { mutableStateOf(false) }
    var priceOfferText by remember { mutableStateOf("") }
    var showLocationDialog by remember { mutableStateOf(false) }
    // Index of the image opened fullscreen; null = closed
    var lightboxImage by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.sendAttachment(context, uri, isImage = true)
    }
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.sendAttachment(context, uri, isImage = false)
    }

    val chatError by viewModel.error.collectAsState()
    LaunchedEffect(chatError) {
        chatError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (showLocationDialog) {
        LocationShareDialog(
            onDismiss = { showLocationDialog = false },
            onSend = { loc ->
                showLocationDialog = false
                viewModel.sendMessage(
                    content = "📍 ${loc.label}", type = "location", location = loc,
                )
            },
        )
    }

    lightboxImage?.let { url ->
        at.nimmdas.app.ui.components.FullscreenGallery(
            images = listOf(url),
            startIndex = 0,
            onDismiss = { lightboxImage = null },
        )
    }

    LaunchedEffect(threadId) { viewModel.loadThread(threadId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        // union, not imePadding().navigationBarsPadding(): the two would stack and leave
        // a gap above the keyboard. The IME inset already covers the navigation bar.
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        topBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }

                    // Partner avatar
                    Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
                        if (partnerAvatar != null) {
                            val avatarUrl = partnerAvatar!!.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                            AsyncImage(model = avatarUrl, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(partnerName.take(1).uppercase(), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        Text(partnerName.ifEmpty { "Chat" }, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (listingTitle.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(listingTitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f, fill = false))
                                if (listingStatus == "sold") {
                                    Surface(Modifier.padding(start = 4.dp), color = Color(0xFF3B82F6).copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                                        Text("Verkauft", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                } else if (listingStatus == "reserved") {
                                    Surface(Modifier.padding(start = 4.dp), color = Color(0xFFEAB308).copy(0.1f), shape = RoundedCornerShape(4.dp)) {
                                        Text("Reserviert", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCA8A04), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Call Button
                    IconButton(onClick = { 
                        onCallRequested?.invoke(
                            viewModel.partnerId,
                            partnerName.ifEmpty { "Anonym" },
                            partnerAvatar,
                            viewModel.listingId
                        ) 
                    }) {
                        Icon(Icons.Filled.Phone, "Anrufen", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Actions Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, "Optionen")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isSeller && listingStatus != "sold") {
                                DropdownMenuItem(
                                    text = { Text("Als verkauft markieren", color = Color(0xFF10B981)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setListingStatus("sold")
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981)) }
                                )
                                if (listingStatus != "reserved") {
                                    DropdownMenuItem(
                                        text = { Text("Reservieren", color = Color(0xFFCA8A04)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.setListingStatus("reserved")
                                        },
                                        leadingIcon = { Icon(Icons.Filled.AccessTime, null, tint = Color(0xFFCA8A04)) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Reservierung aufheben") },
                                        onClick = {
                                            showMenu = false
                                            viewModel.setListingStatus("active")
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, null) }
                                    )
                                }
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            }
                            
                            if (!isSeller) {
                                DropdownMenuItem(
                                    text = { Text("Treffpunkt anfragen") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.sendQuickReply("Können wir einen Treffpunkt vereinbaren? Bitte teile deinen Standort.")
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Place, null) }
                                )
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            }
                            
                            DropdownMenuItem(
                                text = { Text("Benutzer melden", color = Color(0xFFEF4444)) },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(context, "Benutzer gemeldet. Unser Trust & Safety Team wird den Chatverlauf prüfen.", Toast.LENGTH_LONG).show()
                                },
                                leadingIcon = { Icon(Icons.Filled.Report, null, tint = Color(0xFFEF4444)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Blockieren", color = Color(0xFFEF4444)) },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(context, "Benutzer blockiert.", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Filled.Block, null, tint = Color(0xFFEF4444)) }
                            )
                        }
                    }

                    // Listing thumbnail
                    if (listingImage != null) {
                        val imgUrl = listingImage!!.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                        AsyncImage(model = imgUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { 
                                    val lid = threadId.split("_", limit = 2).getOrNull(1)
                                    if (lid != null) onListingClick?.invoke(lid)
                                })
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        },
        bottomBar = {
            Column {
                // Quick replies — same six as on the website, always reachable.
                if (showQuickReplies) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QUICK_REPLIES.forEach { (label, text) ->
                            QuickReplyChip(label) {
                                inputText = text
                                showQuickReplies = false
                            }
                        }
                    }
                }

                // Price offer composer
                if (showPriceOffer) {
                    Surface(color = MaterialTheme.colorScheme.primary.copy(0.06f),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = priceOfferText,
                                onValueChange = { priceOfferText = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                                placeholder = { Text("Dein Preisvorschlag in €", fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                leadingIcon = { Text("€", fontWeight = FontWeight.Bold) },
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { showPriceOffer = false; priceOfferText = "" }) {
                                Text("Abbrechen", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Input bar
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showAttachMenu = true }) {
                                Icon(Icons.Filled.AddCircleOutline, "Anhang",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(showAttachMenu, { showAttachMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Bild senden") },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    onClick = { showAttachMenu = false; imagePicker.launch("image/*") },
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF senden") },
                                    leadingIcon = { Icon(Icons.Filled.Description, null) },
                                    onClick = { showAttachMenu = false; docPicker.launch(arrayOf("application/pdf")) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Preisvorschlag") },
                                    leadingIcon = { Icon(Icons.Filled.LocalOffer, null) },
                                    onClick = { showAttachMenu = false; showPriceOffer = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Standort senden") },
                                    leadingIcon = { Icon(Icons.Filled.Place, null) },
                                    onClick = { showAttachMenu = false; showLocationDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Schnellantworten") },
                                    leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                    onClick = { showAttachMenu = false; showQuickReplies = !showQuickReplies },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Nachricht...", fontSize = 14.sp) },
                            singleLine = false,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        val offerValue = priceOfferText.replace(",", ".").toDoubleOrNull()
                        val canSend = if (showPriceOffer) (offerValue ?: 0.0) > 0 else inputText.isNotBlank()
                        FilledIconButton(
                            onClick = {
                                if (showPriceOffer && offerValue != null && offerValue > 0) {
                                    val note = inputText.trim()
                                    viewModel.sendMessage(
                                        content = if (note.isEmpty()) "Mein Preisvorschlag: € ${offerValue.toInt()}" else note,
                                        type = "price_offer", priceOffer = offerValue,
                                    )
                                    showPriceOffer = false
                                    priceOfferText = ""
                                } else {
                                    viewModel.sendMessage(inputText.trim())
                                }
                                inputText = ""
                                showQuickReplies = false
                            },
                            enabled = canSend && !isSending,
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, "Senden", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading && messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Schreib die erste Nachricht!", fontWeight = FontWeight.SemiBold)
                    Text("Nutze die Schnellantworten unten", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.15f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                var lastDate = ""
                items(messages) { message ->
                    // Date separator
                    val msgDate = message.timestamp?.take(10) ?: ""
                    if (msgDate != lastDate && msgDate.isNotEmpty()) {
                        lastDate = msgDate
                        DateSeparator(msgDate)
                    }
                    
                    val isSystem = message.messageType in listOf(
                        "offer_accepted", "offer_declined", "reservation", "sold",
                        "coin_redeem_accepted", "coin_redeem_declined",
                    )

                    if (isSystem) {
                        SystemMessageBubble(message)
                    } else {
                        val isMe = message.senderId == viewModel.currentUserId
                        MessageBubble(
                            message = message,
                            isMe = isMe,
                            onOfferResponse = { id, accept -> viewModel.respondToOffer(id, accept) },
                            onImageClick = { lightboxImage = it },
                            onDocumentClick = { url, name -> onOpenDocument?.invoke(url, name) },
                            onLocationClick = { loc ->
                                // Coordinates open the map app; a plain address is searched instead.
                                val uri = if (loc.lat != 0.0 || loc.lng != 0.0) {
                                    android.net.Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(${android.net.Uri.encode(loc.label)})")
                                } else {
                                    android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(loc.label)}")
                                }
                                try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Keine Karten-App gefunden", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(0.08f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(0.2f))
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DateSeparator(dateStr: String) {
    val label = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return
        val cal = Calendar.getInstance()
        val today = cal.clone() as Calendar
        cal.time = date
        when {
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Heute"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Gestern"
            else -> SimpleDateFormat("EEEE, d. MMMM", Locale("de", "AT")).format(date)
        }
    } catch (_: Exception) { dateStr }

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f), shape = RoundedCornerShape(50)) {
            Text(label, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SystemMessageBubble(message: Message) {
    val bgColor = when (message.messageType) {
        "offer_accepted", "sold" -> Color(0xFF10B981).copy(0.1f)
        "offer_declined" -> Color(0xFFEF4444).copy(0.1f)
        else -> Color(0xFFEAB308).copy(0.1f)
    }
    val textColor = when (message.messageType) {
        "offer_accepted", "sold" -> Color(0xFF047857)
        "offer_declined" -> Color(0xFFB91C1C)
        else -> Color(0xFFA16207)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        Surface(color = bgColor, shape = RoundedCornerShape(12.dp), border = BorderStroke(0.5.dp, textColor.copy(0.2f))) {
            Text(message.content ?: "", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
    }
}

/** The six quick replies offered on the website. */
private val QUICK_REPLIES = listOf(
    "Noch verfügbar?" to "Hallo! Ist der Artikel noch verfügbar?",
    "Preisvorschlag" to "Hallo! Wäre ein Preisnachlass möglich?",
    "Versandkosten?" to "Hallo! Wie hoch wären die Versandkosten?",
    "Abholung?" to "Hallo! Wann könnte ich den Artikel abholen?",
    "Zustand?" to "Hallo! Können Sie mehr zum Zustand sagen? Gibt es Mängel?",
    "Reservieren" to "Hallo! Könnten Sie den Artikel für mich reservieren? Ich melde mich schnellstmöglich.",
)

@Composable
private fun OfferBadge(text: String, color: Color) {
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 6.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Shares a meeting point. Either the current GPS position or a typed address —
 * the app has no geocoder, so a typed address travels as a label without coordinates.
 */
@Composable
private fun LocationShareDialog(
    onDismiss: () -> Unit,
    onSend: (MessageLocation) -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var gps by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            gps = lastKnownLocation(context)
            if (gps == null) gpsError = "Kein Standort verfügbar."
        } else {
            gpsError = "Standortfreigabe abgelehnt."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standort senden") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("Straße, PLZ, Ort…", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val existing = lastKnownLocation(context)
                        if (existing != null) gps = existing
                        else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Aktuellen Standort verwenden", fontSize = 13.sp)
                }
                gps?.let {
                    Text("✓ Position übernommen (%.4f, %.4f)".format(it.first, it.second),
                        fontSize = 11.sp, color = Color(0xFF059669),
                        modifier = Modifier.padding(top = 6.dp))
                }
                gpsError?.let {
                    Text(it, fontSize = 11.sp, color = Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSend(MessageLocation(
                        lat = gps?.first ?: 0.0,
                        lng = gps?.second ?: 0.0,
                        label = label.ifBlank { "Treffpunkt" },
                    ))
                },
                enabled = label.isNotBlank() || gps != null,
            ) { Text("Senden") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Reads the last fix without starting a new one; null when unavailable or not permitted. */
private fun lastKnownLocation(context: android.content.Context): Pair<Double, Double>? = try {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    listOf(
        android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER,
    ).firstNotNullOfOrNull { p ->
        @Suppress("MissingPermission")
        lm.getLastKnownLocation(p)?.let { it.latitude to it.longitude }
    }
} catch (_: SecurityException) { null } catch (_: Exception) { null }

@Composable
private fun MessageBubble(
    message: Message,
    isMe: Boolean,
    onOfferResponse: (String, Boolean) -> Unit = { _, _ -> },
    onImageClick: (String) -> Unit = {},
    onDocumentClick: (String, String) -> Unit = { _, _ -> },
    onLocationClick: (MessageLocation) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            color = if (isMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
            shadowElevation = if (isMe) 0.dp else 0.5.dp,
            modifier = Modifier.widthIn(min = 60.dp, max = 300.dp)
        ) {
            val onColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (message.messageType == "price_offer") {
                    Text("💰 Preisvorschlag", fontSize = 10.sp, color = onColor.copy(if (isMe) 0.7f else 0.5f))
                    Text(
                        message.priceOffer?.let { "€ ${it.toInt()}" } ?: message.content ?: "",
                        color = onColor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    if (!message.content.isNullOrBlank() && message.priceOffer != null) {
                        Text(message.content, color = onColor.copy(0.8f), fontSize = 13.sp)
                    }
                    // The recipient decides; both sides see the outcome.
                    when (message.offerStatus) {
                        "accepted" -> OfferBadge("✓ Angenommen", Color(0xFF10B981))
                        "declined" -> OfferBadge("✕ Abgelehnt", Color(0xFFEF4444))
                        else -> if (!isMe) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { message.id?.let { onOfferResponse(it, true) } },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("Annehmen", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { message.id?.let { onOfferResponse(it, false) } },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) { Text("Ablehnen", fontSize = 12.sp) }
                            }
                        } else {
                            Text("Warte auf Antwort …", fontSize = 10.sp, color = onColor.copy(0.6f))
                        }
                    }
                } else if (message.messageType == "image" && message.image != null) {
                    val imgUrl = message.image.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                    AsyncImage(
                        model = imgUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(imgUrl) },
                    )
                    if (!message.content.isNullOrEmpty() && message.content != "📷 Foto" && message.content != "📷 Bild") {
                        Spacer(Modifier.height(4.dp))
                        Text(message.content, color = onColor, fontSize = 14.sp)
                    }
                } else if (message.messageType == "document" && message.document != null) {
                    val doc = message.document
                    val docUrl = doc.url.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                    Row(
                        Modifier.clickable { onDocumentClick(docUrl, doc.name) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(30.dp), tint = onColor)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(doc.name, color = onColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${doc.size / 1024} KB · PDF", color = onColor.copy(0.6f), fontSize = 10.sp)
                        }
                    }
                } else if (message.messageType == "location") {
                    Row(
                        Modifier.clickable {
                            message.location?.let { onLocationClick(it) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, null, tint = if (isMe) onColor else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Standort", fontSize = 10.sp, color = onColor.copy(0.6f))
                            Text(
                                message.location?.label?.ifBlank { null } ?: message.content ?: "Standort",
                                color = onColor, fontSize = 14.sp,
                            )
                        }
                    }
                } else if (message.messageType == "call_summary" || message.messageType == "call_missed") {
                    val missed = message.messageType == "call_missed"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (missed) Icons.Filled.PhoneMissed else Icons.Filled.PhoneInTalk,
                            null, Modifier.size(20.dp),
                            tint = if (missed) Color(0xFFEF4444) else onColor,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(if (missed) "Verpasster Anruf" else "Anruf beendet",
                                color = onColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            message.callDuration?.takeIf { it > 0 }?.let { d ->
                                Text("%d:%02d min".format(d / 60, d % 60), color = onColor.copy(0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Text(
                        message.content ?: "",
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Time
                    val timeStr = message.timestamp?.let {
                        try {
                            val formats = listOf(
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                            )
                            var date: Date? = null
                            for (fmt in formats) {
                                fmt.timeZone = TimeZone.getTimeZone("UTC")
                                try { date = fmt.parse(it); break } catch (_: Exception) {}
                            }
                            date?.let { d ->
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
                            } ?: ""
                        } catch (_: Exception) { "" }
                    } ?: ""
                    Text(timeStr, fontSize = 10.sp,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(0.6f)
                                else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    // Read receipt
                    if (isMe && message.read == true) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.DoneAll, "Gelesen", Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onPrimary.copy(0.7f))
                    }
                }
            }
        }
    }
}
