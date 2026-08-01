package at.nimmdas.app.ui.screens.listing

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import at.nimmdas.app.data.PriceRating
import at.nimmdas.app.data.evaluatePrice
import at.nimmdas.app.data.htmlToAnnotatedString
import at.nimmdas.app.data.model.*
import java.text.NumberFormat
import java.util.Locale
import at.nimmdas.app.ui.components.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListingDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient
    private val _listing = MutableStateFlow<Listing?>(null)
    val listing = _listing.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isSaved = MutableStateFlow(false)
    val isSaved = _isSaved.asStateFlow()
    private val _similarListings = MutableStateFlow<List<Listing>>(emptyList())
    val similarListings = _similarListings.asStateFlow()

    /** Sends the quick question straight to the seller, like the website's AutoMessageButton. */
    fun sendQuickMessage(listingId: String, receiverId: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                apiClient.api.sendMessage(
                    at.nimmdas.app.data.model.SendMessageRequest(
                        content = content, listingId = listingId, receiverId = receiverId,
                    )
                ).isSuccessful
            } catch (_: Exception) { false }
            onResult(ok)
        }
    }

    fun loadListing(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiClient.api.getListingById(id)
                if (response.isSuccessful) {
                    _listing.value = response.body()
                    // Load similar listings
                    val cat = response.body()?.category
                    if (cat != null) {
                        try {
                            val similar = apiClient.api.search(category = cat, limit = 6)
                            if (similar.isSuccessful) {
                                _similarListings.value = (similar.body()?.listings ?: emptyList())
                                    .filter { it.id != id }.take(6)
                            }
                        } catch (_: Exception) {}
                    }
                }
                // Check watchlist
                try {
                    val wl = apiClient.api.getWatchlist()
                    if (wl.isSuccessful) {
                        _isSaved.value = wl.body()?.savedListings?.any { it.id == id } == true
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch {
            _currentUserId.value = apiClient.getUserId()
        }
    }
    fun toggleSave(listingId: String) {
        viewModelScope.launch {
            try {
                val resp = apiClient.api.toggleWatchlist(WatchlistRequest(listingId))
                if (resp.isSuccessful) _isSaved.value = resp.body()?.saved == true
            } catch (_: Exception) {}
        }
    }

    fun updateStatus(id: String, status: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resp = apiClient.api.updateListingStatus(id, StatusUpdateRequest(status))
                if (resp.isSuccessful) {
                    _listing.value = _listing.value?.copy(status = status)
                    onDone()
                }
            } catch (_: Exception) {}
        }
    }

    // Seller reputation, loaded once the listing (and thus the seller id) is known
    private val _sellerRatings = MutableStateFlow<RatingsResponse?>(null)
    val sellerRatings = _sellerRatings.asStateFlow()

    fun loadSellerRatings(userId: String) {
        viewModelScope.launch {
            try {
                val r = apiClient.api.getRatings(userId)
                if (r.isSuccessful) _sellerRatings.value = r.body()
            } catch (_: Exception) { /* reputation is optional */ }
        }
    }

    /** Reports the listing to moderation. [onDone] gets whether it was accepted. */
    fun reportListing(id: String, reason: String, description: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = try {
                apiClient.api.reportListing(
                    ReportRequest(listingId = id, reason = reason, description = description.ifBlank { null })
                ).isSuccessful
            } catch (_: Exception) { false }
            onDone(ok)
        }
    }

    fun deleteListing(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val resp = apiClient.api.deleteListing(id)
                if (resp.isSuccessful) onDone()
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onChat: (String) -> Unit = {},
    onListingClick: (String) -> Unit = {},
    onSellerClick: (String) -> Unit = {},
    onEditClick: (String) -> Unit = {},
    /** Opens a web page in the in-app browser (url, title). */
    onShadowmap: (String, String) -> Unit = { _, _ -> },
    /** Opens a PDF or video attachment in the in-app viewer (url, title). */
    onOpenDocument: (String, String) -> Unit = { _, _ -> },
    viewModel: ListingDetailViewModel = viewModel()
) {
    val listing by viewModel.listing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val similarListings by viewModel.similarListings.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    // Index of the photo shown fullscreen; null = gallery closed
    var galleryIndex by remember { mutableStateOf<Int?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var descExpanded by remember { mutableStateOf(false) }

    val sellerRatings by viewModel.sellerRatings.collectAsState()

    LaunchedEffect(listingId) { viewModel.loadListing(listingId) }
    LaunchedEffect(listing?.sellerId?.id) {
        listing?.sellerId?.id?.takeIf { it.isNotBlank() }?.let { viewModel.loadSellerRatings(it) }
    }

    galleryIndex?.let { idx ->
        FullscreenGallery(
            images = listing?.images ?: emptyList(),
            startIndex = idx,
            onDismiss = { galleryIndex = null },
        )
    }

    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, details ->
                showReport = false
                viewModel.reportListing(listingId, reason, details) { ok ->
                    android.widget.Toast.makeText(
                        context,
                        if (ok) "Danke – wir prüfen das Inserat." else "Melden fehlgeschlagen",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing?.title ?: "Inserat", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (currentUserId != null && currentUserId == listing?.sellerId?.id) {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "Optionen") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Bearbeiten") },
                                onClick = { showMenu = false; onEditClick(listingId) }
                            )
                            if (listing?.status != "sold") {
                                DropdownMenuItem(
                                    text = { Text("Als verkauft markieren") },
                                    onClick = { showMenu = false; viewModel.updateStatus(listingId, "sold") { onBack() } }
                                )
                            }
                            if (listing?.status != "reserved") {
                                DropdownMenuItem(
                                    text = { Text("Reservieren") },
                                    onClick = { showMenu = false; viewModel.updateStatus(listingId, "reserved") { viewModel.loadListing(listingId) } }
                                )
                            }
                            if (listing?.status != "active") {
                                DropdownMenuItem(
                                    text = { Text("Aktivieren") },
                                    onClick = { showMenu = false; viewModel.updateStatus(listingId, "active") { viewModel.loadListing(listingId) } }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Löschen", color = Color.Red) },
                                onClick = { showMenu = false; viewModel.deleteListing(listingId) { onBack() } }
                            )
                        }
                    } else {
                        // Save
                        IconButton(onClick = { viewModel.toggleSave(listingId) }) {
                            Icon(if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Merken",
                                tint = if (isSaved) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface)
                        }
                        // Share
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${BuildConfig.API_BASE_URL}/listing/$listingId")
                                putExtra(Intent.EXTRA_SUBJECT, listing?.title ?: "")
                            }
                            context.startActivity(Intent.createChooser(intent, "Teilen"))
                        }) { Icon(Icons.Filled.Share, "Teilen") }
                        // Report — only makes sense on someone else's listing
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("🚩 Inserat melden") },
                                onClick = { showMenu = false; showReport = true },
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 3.dp)
            }
        } else if (listing == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Inserat nicht gefunden", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        } else {
            val l = listing!!
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {

                // ═══ IMAGE GALLERY ═══
                val images = l.images ?: emptyList()
                if (images.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { images.size })
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            val url = images[page].let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                            AsyncImage(
                                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clickable { galleryIndex = page },
                            )
                        }
                        // Hint that the photo opens fullscreen
                        Surface(
                            Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            shape = RoundedCornerShape(8.dp), color = Color.Black.copy(0.55f),
                        ) {
                            Text("⤢ Vergrößern", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        // Gradient
                        Box(Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.3f)))))
                        // Dots
                        if (images.size > 1) {
                            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(images.size) { i ->
                                    Box(Modifier.size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (i == pagerState.currentPage) Color.White else Color.White.copy(0.5f)))
                                }
                            }
                        }
                        // Count
                        Surface(color = Color.Black.copy(0.6f), shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                            Text("${pagerState.currentPage + 1}/${images.size}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        // Status badges
                        Column(Modifier.align(Alignment.TopStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (l.status == "reserved") StatusBadge("🟡 Reserviert", Color(0xFFEAB308))
                            if (l.status == "sold") StatusBadge("✅ Verkauft", Color(0xFF3B82F6))
                        }
                    }

                    // ── Thumbnail strip, like on the website
                    if (images.size > 1) {
                        val scope = rememberCoroutineScope()
                        LazyRow(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(images) { i, img ->
                                val selected = i == pagerState.currentPage
                                Box(
                                    Modifier.size(72.dp, 56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outline.copy(0.25f),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                                ) {
                                    AsyncImage(
                                        model = img.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Action row: share · report · views · age (mirrors the website)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        Modifier.clickable {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${BuildConfig.API_BASE_URL}/listing/$listingId")
                                putExtra(Intent.EXTRA_SUBJECT, l.title)
                            }
                            context.startActivity(Intent.createChooser(intent, "Teilen"))
                        },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f)),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                            Text("Teilen", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (currentUserId == null || currentUserId != l.sellerId?.id) {
                        Surface(
                            Modifier.clickable { showReport = true },
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent,
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Filled.Flag, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Text("Melden", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Visibility, null, Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                        Text("${l.views ?: 0} Aufrufe", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                    }
                }

                // ═══ PRICE + TITLE ═══
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        val discounted = l.originalPrice != null && l.originalPrice > (l.price ?: 0.0)
                        // Original price (strikethrough)
                        if (discounted && l.originalPrice != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("€ ${formatPrice(l.originalPrice)}", fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                val pct = ((1 - (l.price ?: 0.0) / l.originalPrice) * 100).toInt()
                                Surface(color = Color(0xFFEF4444), shape = RoundedCornerShape(50)) {
                                    Text("-$pct% SALE", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        val priceText = when (l.priceType) {
                            "verschenken" -> "Gratis"
                            "auf_anfrage" -> "Auf Anfrage"
                            "vb" -> "€ ${formatPrice(l.price)} VB"
                            else -> if (l.price != null && l.price > 0) "€ ${formatPrice(l.price)}" else "Gratis"
                        }
                        // Reduced prices are red; everything else uses the brand gradient.
                        if (discounted) {
                            Text(priceText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEF4444))
                        } else {
                            Text(
                                priceText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                                style = LocalTextStyle.current.copy(
                                    brush = Brush.horizontalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF10B981), Color(0xFF14B8A6))
                                    )
                                ),
                            )
                        }
                        if (l.priceType == "vb") {
                            Text("Verhandlungsbasis", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(l.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)

                        Spacer(Modifier.height(8.dp))
                        // Quick info chips
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            l.location?.let { InfoChip("📍 $it") }
                            l.condition?.let { InfoChip(when (it) { "neu" -> "✨ Neu"; "defekt" -> "⚠️ Defekt"; else -> "♻️ Gebraucht" }) }
                            if (l.shipping == true) InfoChip("📦 Versand")
                            Text("👁 ${l.views ?: 0}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.align(Alignment.CenterVertically))
                            l.createdAt?.let { Text(timeAgo(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                modifier = Modifier.align(Alignment.CenterVertically)) }
                        }
                    }
                }

                // ═══ MARKTPREIS-CHECK ═══
                // Directly under the price: "is this a good deal?" is the first question
                // after seeing what it costs.
                PriceCheckCard(l)

                // ═══ SPECS GRID ═══
                val specs = buildSpecs(l)
                if (specs.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("MERKMALE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            Spacer(Modifier.height(12.dp))
                            // 2-column grid
                            val rows = specs.chunked(2)
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (label, value) ->
                                        Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), shape = RoundedCornerShape(10.dp)) {
                                            Column(Modifier.padding(10.dp)) {
                                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                                Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    if (row.size < 2) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // ═══ ENERGIEAUSWEIS (Immobilien) ═══
                EnergyCertificateCard(l)

                // ═══ DOKUMENTE (Grundrisse, Exposés) ═══
                DocumentsCard(l, onOpenDocument)

                // ═══ EQUIPMENT (Autos) ═══
                if (l.category == "Autos" && !l.equipment.isNullOrEmpty()) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("⚡ AUSSTATTUNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Surface(color = MaterialTheme.colorScheme.primary.copy(0.1f), shape = RoundedCornerShape(50)) {
                                    Text("${l.equipment!!.size} Merkmale", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Flow-style wrap
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                l.equipment!!.forEach { item ->
                                    Surface(color = Color(0xFF10B981).copy(0.1f), shape = RoundedCornerShape(50),
                                        border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(0.3f))) {
                                        Text("✓ $item", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ DESCRIPTION ═══
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("BESCHREIBUNG", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        Spacer(Modifier.height(8.dp))
                        // Descriptions may contain HTML (OpenImmo imports); render it styled
                        // instead of printing the raw markup.
                        val desc = remember(l.id, l.description) {
                            (l.description ?: "Keine Beschreibung").htmlToAnnotatedString()
                        }
                        // Long texts (especially imported real-estate exposés) are collapsed
                        // so the rest of the page stays reachable.
                        val collapsible = desc.length > 320
                        Text(
                            desc, fontSize = 14.sp, lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
                            maxLines = if (!collapsible || descExpanded) Int.MAX_VALUE else 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (collapsible) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (descExpanded) "Weniger anzeigen ▲" else "Mehr anzeigen ▼",
                                Modifier.clickable { descExpanded = !descExpanded },
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // Tags
                        if (!l.tags.isNullOrEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                l.tags!!.forEach { tag ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
                                        Text("#$tag", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══ KARTE ═══
                // After the details: once the item is interesting, "where is it?" follows.
                LocationMapCard(l, onShadowmap)

                // ═══ ANSPRECHPARTNER (Immobilien-Importe) ═══
                // Grouped with the seller block — both are "who do I contact".
                ContactPersonCard(l)

                // ═══ SELLER CARD ═══
                val seller = l.sellerId
                if (seller != null) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { onSellerClick(seller.id) },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)) {
                        // Profile banner (only when moderation approved it)
                        val banner = seller.profileBanner?.takeIf {
                            it.isNotBlank() && seller.bannerApproved == true
                        }
                        if (banner != null) {
                            AsyncImage(
                                model = if (banner.startsWith("http")) banner else "${BuildConfig.API_BASE_URL}$banner",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                            )
                        }
                        Column(Modifier.padding(16.dp)) {
                            Text("ANBIETER", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Avatar — relative paths need the API host prefixed
                                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(0.1f))) {
                                    val av = seller.avatar?.takeIf { it.isNotBlank() }
                                    if (av != null) {
                                        AsyncImage(
                                            model = if (av.startsWith("http")) av else "${BuildConfig.API_BASE_URL}$av",
                                            contentDescription = seller.name,
                                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    } else {
                                        Icon(Icons.Filled.Person, null, Modifier.align(Alignment.Center).size(24.dp),
                                            tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(seller.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        if (seller.verified == true) Text("✅", fontSize = 12.sp)
                                    }
                                    // Reputation
                                    val rs = sellerRatings
                                    if (rs != null && rs.count > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            repeat(5) { i ->
                                                Icon(
                                                    if (i < (rs.average + 0.5).toInt()) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    null, Modifier.size(13.dp), tint = Color(0xFFFBBF24),
                                                )
                                            }
                                            Spacer(Modifier.width(4.dp))
                                            Text("${rs.average} (${rs.count})", fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                                        }
                                    }
                                    seller.createdAt?.let {
                                        Text("Dabei seit ${it.take(4)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                    }
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                            }
                            // Seller stats
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SellerStat("Follower", "${seller.followers?.size ?: 0}", Modifier.weight(1f))
                                SellerStat("Antwort", seller.responseTime?.let {
                                    if (it < 60) "${it}m" else "${it / 60}h"
                                } ?: "—", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            // Contact button
                            Button(onClick = { onChat(seller.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Filled.Chat, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Verkäufer kontaktieren", fontWeight = FontWeight.Bold)
                            }

                            // Quick question — mirrors the website's "Ist noch verfügbar?" shortcut.
                            if (seller.id != currentUserId && seller.id.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                var quickText by remember(l.id) {
                                    mutableStateOf("Hallo! Ist \"${l.title}\" noch verfügbar?")
                                }
                                var sending by remember(l.id) { mutableStateOf(false) }
                                var sent by remember(l.id) { mutableStateOf(false) }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = quickText,
                                        onValueChange = { quickText = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Ist noch verfügbar?", fontSize = 13.sp) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        enabled = !sending && !sent,
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            if (quickText.isBlank()) return@FilledIconButton
                                            sending = true
                                            viewModel.sendQuickMessage(l.id, seller.id, quickText) { ok ->
                                                sending = false
                                                sent = ok
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (ok) "Anfrage gesendet" else "Senden fehlgeschlagen",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                                if (ok) onChat(seller.id)
                                            }
                                        },
                                        enabled = !sending && !sent,
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        if (sending) {
                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Icon(if (sent) Icons.Filled.Check else Icons.Filled.Send,
                                                null, Modifier.size(18.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Lock, null, Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                    Text("Sichere Nachrichten · ${l.views ?: 0} Aufrufe", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                }
                            }
                        }
                    }
                }

                // ═══ SIMILAR LISTINGS ═══
                if (similarListings.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Ähnliche Inserate", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        similarListings.forEach { sim ->
                            ListingCard(sim, onClick = { onListingClick(sim.id) }, modifier = Modifier.width(170.dp), viewMode = "grid")
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

// ── Helper Composables ──

@Composable
private fun InfoChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.7f), shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(color = Color.White.copy(0.95f), shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SellerStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        }
    }
}

// ── Build Specs List ──
/**
 * Attached PDFs (floor plans, brochures, zoning documents). Imported real-estate
 * listings frequently carry several; tapping opens them in the browser.
 */
@Composable
private fun DocumentsCard(l: Listing, onOpenDocument: (String, String) -> Unit) {
    val docs = l.documents?.filter { !it.url.isNullOrBlank() }.orEmpty()
    if (docs.isEmpty()) return

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("📄 DOKUMENTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Spacer(Modifier.height(10.dp))
            docs.forEach { doc ->
                val url = doc.url ?: return@forEach
                val clean = url.substringBefore('?').lowercase()
                val isPdf = clean.endsWith(".pdf")
                val isVideo = clean.endsWith(".mp4") || clean.endsWith(".webm") ||
                    clean.endsWith(".mov") || clean.endsWith(".m4v")
                val label = when { isPdf -> "PDF"; isVideo -> "VIDEO"; else -> "LINK" }
                val labelColor = when {
                    isPdf -> Color(0xFFDC2626)
                    isVideo -> Color(0xFF7C3AED)
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            onOpenDocument(url, doc.title?.takeIf { it.isNotBlank() } ?: "Dokument")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = labelColor.copy(0.12f)) {
                        Text(label,
                            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = labelColor)
                    }
                    Text(doc.title?.takeIf { it.isNotBlank() } ?: "Dokument",
                        Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Filled.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                }
            }
        }
    }
}

/** The Austrian energy-class scale, in order, with the website's exact colours. */
private val ENERGY_SCALE = listOf(
    "A++" to Color(0xFF005A36),
    "A+" to Color(0xFF188038),
    "A" to Color(0xFF34A853),
    "B" to Color(0xFF8BC34A),
    "C" to Color(0xFFFBBC04),
    "D" to Color(0xFFF97316),
    "E" to Color(0xFFEA4335),
    "F" to Color(0xFFC5221F),
    "G" to Color(0xFFA50F15),
    "H" to Color(0xFF7F0000),
)

/** Classes with light fills need dark text. */
private fun energyTextColor(cls: String) = if (cls == "B" || cls == "C") Color.Black else Color.White

private fun energyClassColor(cls: String?): Color {
    val key = cls?.uppercase()?.trim()?.take(3)
    return ENERGY_SCALE.firstOrNull { it.first == key }?.second
        ?: ENERGY_SCALE.firstOrNull { it.first == key?.take(2) }?.second
        ?: ENERGY_SCALE.firstOrNull { it.first == key?.take(1) }?.second
        ?: Color(0xFF64748B)
}

/** What each class means — same wording as the website's explainer. */
private val ENERGY_EXPLAINER = listOf(
    "A++" to "< 10 kWh/m²a: Passivhaus (Höchster Standard, minimaler Energiebedarf)",
    "A+" to "< 15 kWh/m²a: Passivhaus-Standard (Hervorragende Dämmung)",
    "A" to "< 25 kWh/m²a: Niedrigstenergiehaus (Sehr guter Standard)",
    "B" to "< 50 kWh/m²a: Niedrigenergiehaus (Typischer moderner Neubau)",
    "C" to "< 100 kWh/m²a: Geringer Energiebedarf (Standardneubau / Sanierter Altbau)",
    "D" to "< 150 kWh/m²a: Durchschnittlicher Bedarf (Typisch für sanierte Altbauten)",
    "E" to "< 200 kWh/m²a: Mäßiger Dämmstandard (Ältere Gebäude mit Teilsanierung)",
    "F" to "< 250 kWh/m²a: Hoher Energiebedarf (Dringender Sanierungsbedarf)",
    "G" to "> 250 kWh/m²a: Sehr hoher Bedarf (Unrenovierter Altbau, keine Dämmung)",
)

/** Energy price used for the heating-cost estimate, matching the website. */
private const val ENERGY_PRICE_PER_KWH = 0.15

/**
 * Energieausweis block for real estate — mirrors the website: raw HWB / f-GEE values, the
 * A++…H class scale with the current class marked, an explainer, and a heating-cost
 * estimate derived from HWB × area × energy price.
 */
@Composable
private fun EnergyCertificateCard(l: Listing) {
    val hasData = l.hwbValue != null || l.hwbClass != null ||
        l.fgeeValue != null || l.fgeeClass != null || !l.heatingType.isNullOrBlank()
    if (l.category != "Immobilien" || !hasData) return

    var explainerOpen by remember { mutableStateOf(false) }
    val nf = NumberFormat.getNumberInstance(Locale.GERMANY)
    val activeClass = l.hwbClass?.uppercase()?.trim()?.let { c ->
        ENERGY_SCALE.map { it.first }.firstOrNull { it == c.take(3) || it == c.take(2) || it == c.take(1) }
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🔥 ENERGIEAUSWEIS / HEIZUNG", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Spacer(Modifier.height(12.dp))

            l.hwbValue?.let { EnergyRow("HWB (kWh/m²/Jahr):", nf.format(it)) }
            l.hwbClass?.takeIf { it.isNotBlank() }?.let {
                EnergyRow("HWB Energieklasse:", it, energyClassColor(it))
            }
            l.fgeeValue?.let { EnergyRow("f-GEE:", nf.format(it)) }
            l.fgeeClass?.takeIf { it.isNotBlank() }?.let {
                EnergyRow("f-GEE Energieklasse:", it, energyClassColor(it))
            }
            l.heatingType?.takeIf { it.isNotBlank() }?.let { EnergyRow("Heizung:", it) }

            if (activeClass != null) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("ENERGIEEFFIZIENZKLASSE", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    Text("Klasse $activeClass", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom) {
                    ENERGY_SCALE.forEach { (cls, color) ->
                        val active = cls == activeClass
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (active) {
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary) {
                                    Text("IST", Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Spacer(Modifier.height(3.dp))
                            }
                            Box(
                                Modifier.fillMaxWidth().height(if (active) 34.dp else 28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) color else color.copy(alpha = 0.35f))
                                    .then(
                                        if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(6.dp)) else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(cls, fontSize = if (active) 11.sp else 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (active) energyTextColor(cls) else Color.White.copy(0.85f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                Modifier.fillMaxWidth().clickable { explainerOpen = !explainerOpen },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("💡 Was bedeuten die Energieklassen?", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Icon(if (explainerOpen) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                            null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    if (explainerOpen) {
                        Spacer(Modifier.height(10.dp))
                        ENERGY_EXPLAINER.forEach { (cls, desc) ->
                            Row(Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(shape = RoundedCornerShape(4.dp), color = energyClassColor(cls)) {
                                    Text(cls, Modifier.width(30.dp).padding(vertical = 2.dp),
                                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                        color = energyTextColor(cls),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                                Text(desc, fontSize = 11.sp, lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.75f))
                            }
                        }
                    }
                }
            }

            val area = l.squareMeters
            val hwb = l.hwbValue
            if (area != null && area > 0 && hwb != null && hwb > 0) {
                val demand = area * hwb
                val cost = demand * ENERGY_PRICE_PER_KWH
                Spacer(Modifier.height(12.dp))
                Surface(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🧮 Heizkosten-Schätzer für dieses Objekt", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EnergyStat("FLÄCHE", "${area.formatMeasure()} m²", Modifier.weight(1f))
                            EnergyStat("ENERGIEBEDARF", "${nf.format(demand.toLong())} kWh/Jahr", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.08f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("HEIZKOSTEN (CA.)", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.primary.copy(0.8f))
                                Text("€ ${nf.format(cost.toLong())} / Jahr", fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "* Berechnung mit einem durchschnittlichen Energiepreis von € 0,15 pro kWh " +
                            "(z.B. Fernwärme/Wärmepumpe) und dem Norm-Heizwärmebedarf (HWB). Der tatsächliche " +
                            "Verbrauch hängt stark vom Heizverhalten, dem Heizungssystem " +
                            "(${l.heatingType?.takeIf { it.isNotBlank() } ?: "nicht angegeben"}) und der Witterung ab.",
                            fontSize = 9.sp, lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnergyRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.65f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.08f))
}

@Composable
private fun EnergyStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Reasons offered when reporting a listing — mirrors the web moderation categories. */
private val REPORT_REASONS = listOf(
    "Betrug / Scam",
    "Verbotener Artikel",
    "Falsche Angaben",
    "Anstößiger Inhalt",
    "Duplikat",
    "Sonstiges",
)

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by remember { mutableStateOf(REPORT_REASONS.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Inserat melden", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Warum meldest du dieses Inserat?", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                Spacer(Modifier.height(2.dp))
                REPORT_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = details, onValueChange = { details = it },
                    label = { Text("Details (optional)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason, details) }, shape = RoundedCornerShape(12.dp)) {
                Text("Melden")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * Location map — the app counterpart of the website's MiniMap. Renders nothing when the
 * listing has no usable coordinates.
 */
@Composable
private fun LocationMapCard(l: Listing, onShadowmap: (String, String) -> Unit) {
    val lat = l.coordinates?.lat() ?: return
    val lng = l.coordinates?.lng() ?: return
    if (lat == 0.0 && lng == 0.0) return
    val ctx = LocalContext.current

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        // The map is an Android view; keep the controls as overlays inside its box so
        // nothing is drawn on top of or underneath it unpredictably.
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            ListingMap(lat = lat, lng = lng)

            l.location?.takeIf { it.isNotBlank() }?.let { loc ->
                Surface(
                    Modifier.align(Alignment.BottomStart).padding(10.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(0.92f),
                    shadowElevation = 2.dp,
                ) {
                    Text("📍 $loc", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                Modifier.align(Alignment.BottomEnd).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Real estate gets the same 3D sun-path simulator as the website; it is a
                // WebGL scene, so the app opens the standalone web view of it.
                if (l.category == "Immobilien") {
                    Surface(
                        Modifier.clickable {
                            onShadowmap(
                                "${BuildConfig.API_BASE_URL}/shadowmap?lat=$lat&lng=$lng",
                                "☀️ Sonnenverlauf",
                            )
                        },
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF0F172A).copy(0.92f),
                        shadowElevation = 2.dp,
                    ) {
                        Text("☀️ 3D Sonnenverlauf", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Surface(
                    Modifier.clickable {
                        runCatching {
                            ctx.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng(${android.net.Uri.encode(l.title)})")
                            ))
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(0.92f),
                    shadowElevation = 2.dp,
                ) {
                    Text("Route ↗", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * The dedicated contact person that comes with imported real-estate listings.
 * Renders nothing when the listing has no contact data.
 */
@Composable
private fun ContactPersonCard(l: Listing) {
    val name = l.contactName?.takeIf { it.isNotBlank() } ?: return
    val ctx = LocalContext.current

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ANSPRECHPARTNER", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)) {
                    val photo = l.contactPhoto?.takeIf { it.isNotBlank() }
                    if (photo != null) {
                        AsyncImage(
                            model = if (photo.startsWith("http")) photo else "${BuildConfig.API_BASE_URL}$photo",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(name.take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("Direkter Ansprechpartner", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
                }
            }

            val phone = l.contactPhone?.takeIf { it.isNotBlank() }
            val email = l.contactEmail?.takeIf { it.isNotBlank() }
            if (phone != null || email != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    phone?.let { p ->
                        Button(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:${p.filter { it.isDigit() || it == '+' }}")
                                    ))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Anrufen", fontSize = 13.sp) }
                    }
                    email?.let { e ->
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(android.content.Intent(
                                        android.content.Intent.ACTION_SENDTO,
                                        android.net.Uri.parse("mailto:$e")
                                    ).putExtra(android.content.Intent.EXTRA_SUBJECT, l.title))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("E-Mail", fontSize = 13.sp) }
                    }
                }
                phone?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
            }
        }
    }
}

/** One zone caption under the gauge, sized to that zone's share of the track. */
@Composable
private fun RowScope.GaugeLabel(text: String, color: Color, weight: Float) {
    Text(
        text, Modifier.weight(weight),
        fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color,
        lineHeight = 10.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * Market price check — the app counterpart of the website's PriceEvaluationBadge.
 * Renders nothing when the listing carries no (fresh) evaluation data.
 */
@Composable
private fun PriceCheckCard(l: Listing) {
    val verdict = remember(l.id, l.price, l.estimatedUsedPrice) { evaluatePrice(l) } ?: return
    val price = l.price ?: return
    val color = when (verdict.rating) {
        PriceRating.SUPER -> Color(0xFF059669)
        PriceRating.FAIR -> Color(0xFF2563EB)
        PriceRating.HIGH -> Color(0xFFD97706)
        PriceRating.OVER_NEW -> Color(0xFFE11D48)
    }
    val nf = NumberFormat.getNumberInstance(Locale.GERMANY)

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (verdict.isCar) "📊 Live Marktpreis-Check" else "📊 Gebrauchtpreis-Check",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Surface(color = color.copy(0.12f), shape = RoundedCornerShape(8.dp)) {
                    Text(verdict.label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            Text(
                if (verdict.isCar)
                    "Echtzeit-Preisbewertung. Der aktuelle Marktwert wird laufend aus vergleichbaren Inseraten ermittelt."
                else
                    "Echtzeit-Preisbewertung. Der aktuelle Neupreis wird über geizhals.at geladen und mit dem Zustand dieses Artikels verrechnet.",
                fontSize = 11.sp, lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(22.dp))

            // Marker with a price tooltip, positioned on the same 4-zone scale as the web.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val trackWidth = maxWidth
                val markerX = trackWidth * verdict.gaugePosition
                Column {
                    Box(Modifier.fillMaxWidth().height(24.dp)) {
                        Surface(
                            color = color,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .offset(x = (markerX - 34.dp).coerceIn(0.dp, (trackWidth - 68.dp).coerceAtLeast(0.dp)))
                                .width(68.dp),
                        ) {
                            Text(
                                "€ ${nf.format(price.toLong())}",
                                Modifier.padding(vertical = 3.dp),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.45f).fillMaxHeight().background(Color(0xFF10B981)))
                            Box(Modifier.weight(0.25f).fillMaxHeight().background(Color(0xFF3B82F6)))
                            Box(Modifier.weight(0.20f).fillMaxHeight().background(Color(0xFFF59E0B)))
                            Box(Modifier.weight(0.10f).fillMaxHeight().background(Color(0xFFE11D48)))
                        }
                        Box(
                            Modifier
                                .offset(x = (markerX - 8.dp).coerceIn(0.dp, (trackWidth - 16.dp).coerceAtLeast(0.dp)))
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(3.dp, color, CircleShape)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                GaugeLabel("Super Deal", Color(0xFF059669), 0.45f)
                GaugeLabel("Fairer Preis", Color(0xFF2563EB), 0.25f)
                GaugeLabel("Etwas teuer", Color(0xFFD97706), 0.20f)
                GaugeLabel("Über Neupreis", Color(0xFFE11D48), 0.10f)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                verdict.estimatedNewPrice?.takeIf { it > 0 && !verdict.isCar }?.let { np ->
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                        shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("AKTUELLER NEUPREIS", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            Text("€ ${nf.format(np.toLong())}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("geizhals.at", fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                }
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(0.06f),
                    shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (verdict.isCar) "MARKTWERT" else "FAIRER GEBRAUCHTPREIS",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.primary.copy(0.8f))
                        Text("~ € ${nf.format(verdict.fairPrice.toLong())}", fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        val hint = when (verdict.rating) {
                            PriceRating.SUPER -> "✓ Deutlich unter Wert"
                            PriceRating.FAIR -> "✓ Guter Wert"
                            PriceRating.HIGH -> "Über dem fairen Preis"
                            PriceRating.OVER_NEW -> "Teurer als neu"
                        }
                        Text(hint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
                    }
                }
            }
        }
    }
}

private fun buildSpecs(l: Listing): List<Pair<String, String>> {
    val specs = mutableListOf<Pair<String, String>>()
    l.condition?.let { specs.add("Zustand" to when (it) { "neu" -> "Neuwertig"; "defekt" -> "Defekt"; else -> "Gebraucht" }) }
    l.location?.let { specs.add("Standort" to it) }
    if (l.shipping == true) specs.add("Versand" to "Möglich")
    // Autos
    l.brand?.let { specs.add("Marke" to it) }
    l.model?.let { specs.add("Modell" to it) }
    l.year?.let { specs.add("Baujahr" to "$it") }
    l.mileage?.let { specs.add("Kilometer" to "${java.text.NumberFormat.getInstance(java.util.Locale("de", "AT")).format(it)} km") }
    l.fuelType?.let { specs.add("Treibstoff" to it) }
    l.transmission?.let { specs.add("Getriebe" to it) }
    l.power?.let { specs.add("Leistung" to "$it PS") }
    l.color?.let { specs.add("Farbe" to it) }
    l.material?.let { specs.add("Material" to it) }
    l.registrationDate?.let { specs.add("Erstzulassung" to it) }
    l.tuev?.let { specs.add("§57a bis" to it) }
    l.owners?.let { specs.add("Vorbesitzer" to "$it") }
    if (l.accidentFree == true) specs.add("Unfallfrei" to "✅ Ja")
    // Immobilien
    l.squareMeters?.let { specs.add("Fläche" to "${it.formatMeasure()} m²") }
    l.rooms?.let { specs.add("Zimmer" to it.formatMeasure()) }
    l.propertyType?.let { specs.add("Typ" to if (it == "rent") "Miete" else "Kauf") }
    l.floor?.let { specs.add("Stockwerk" to "$it${l.totalFloors?.let { t -> " von $t" } ?: ""}") }
    l.heatingType?.let { specs.add("Heizung" to it) }
    l.energyClass?.let { specs.add("Energieausweis" to it) }
    l.availableFrom?.let { specs.add("Verfügbar ab" to it) }
    if (l.furnished == true) specs.add("Möbliert" to "✅ Ja")
    if (l.balcony == true) specs.add("Balkon" to "✅ Ja")
    if (l.elevator == true) specs.add("Aufzug" to "✅ Ja")
    if (l.parking == true) specs.add("Parkplatz" to "✅ Ja")
    if (l.cellar == true) specs.add("Keller" to "✅ Ja")
    if (l.garden == true) specs.add("Garten" to "✅ Ja")
    // Jobs
    l.companyName?.let { specs.add("Firma" to it) }
    l.jobType?.let { specs.add("Anstellung" to it) }
    l.salary?.let { specs.add("Gehalt" to it) }
    if (l.homeOffice == true) specs.add("Home-Office" to "✅ Möglich")
    l.startDate?.let { specs.add("Startdatum" to it) }
    // Dienstleistungen
    l.serviceArea?.let { specs.add("Einsatzgebiet" to it) }
    l.availability?.let { specs.add("Verfügbarkeit" to it) }
    l.experience?.let { specs.add("Erfahrung" to it) }
    l.priceUnit?.let { specs.add("Preisart" to "pro $it") }
    return specs
}
