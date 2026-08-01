package at.nimmdas.app.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.ProfileUpdateRequest
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()
    private val _bio = MutableStateFlow("")
    val bio = _bio.asStateFlow()
    private val _phone = MutableStateFlow("")
    val phone = _phone.asStateFlow()
    private val _location = MutableStateFlow("")
    val location = _location.asStateFlow()
    private val _avatar = MutableStateFlow<String?>(null)
    val avatar = _avatar.asStateFlow()
    private val _website = MutableStateFlow("")
    val website = _website.asStateFlow()
    private val _websiteApproved = MutableStateFlow(false)
    val websiteApproved = _websiteApproved.asStateFlow()
    private val _dealerAddress = MutableStateFlow("")
    val dealerAddress = _dealerAddress.asStateFlow()
    private val _dealerUid = MutableStateFlow("")
    val dealerUid = _dealerUid.asStateFlow()
    private val _isDealer = MutableStateFlow(false)
    val isDealer = _isDealer.asStateFlow()
    private val _whatsapp = MutableStateFlow(false)
    val whatsapp = _whatsapp.asStateFlow()
    private val _emailNotifications = MutableStateFlow(true)
    val emailNotifications = _emailNotifications.asStateFlow()
    private val _newsletter = MutableStateFlow(false)
    val newsletter = _newsletter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = apiClient.api.getMe()
                if (res.isSuccessful) {
                    val user = res.body()?.user
                    _name.value = user?.name ?: ""
                    _bio.value = user?.bio ?: ""
                    _phone.value = user?.phone ?: ""
                    _location.value = user?.location ?: ""
                    _avatar.value = user?.avatar
                    _website.value = user?.website ?: ""
                    _websiteApproved.value = user?.websiteApproved == true
                    _dealerAddress.value = user?.dealerAddress ?: ""
                    _dealerUid.value = user?.dealerUid ?: ""
                    _isDealer.value = user?.isDealer() == true
                    _whatsapp.value = user?.whatsapp == true
                    _emailNotifications.value = user?.emailNotifications != false
                    _newsletter.value = user?.newsletterOptIn == true
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun setName(v: String) { _name.value = v }
    fun setBio(v: String) { _bio.value = v }
    fun setPhone(v: String) { _phone.value = v }
    fun setLocation(v: String) { _location.value = v }
    fun setWebsite(v: String) { _website.value = v }
    fun setDealerAddress(v: String) { _dealerAddress.value = v }
    fun setDealerUid(v: String) { _dealerUid.value = v }
    fun setWhatsapp(v: Boolean) { _whatsapp.value = v }
    fun setEmailNotifications(v: Boolean) { _emailNotifications.value = v }
    fun setNewsletter(v: Boolean) { _newsletter.value = v }

    fun save() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Empty strings are sent on purpose so a cleared field is actually cleared;
                // only `name` must never be blank.
                val req = ProfileUpdateRequest(
                    name = _name.value.ifBlank { null },
                    bio = _bio.value,
                    phone = _phone.value,
                    location = _location.value,
                    website = _website.value,
                    dealerAddress = if (_isDealer.value) _dealerAddress.value else null,
                    dealerUid = if (_isDealer.value) _dealerUid.value else null,
                    whatsapp = _whatsapp.value,
                    emailNotifications = _emailNotifications.value,
                    newsletterOptIn = _newsletter.value,
                )
                val res = apiClient.api.updateProfile(req)
                if (res.isSuccessful) {
                    _saveSuccess.value = true
                }
            } catch (_: Exception) {}
            _isSaving.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = viewModel()
) {
    val name by viewModel.name.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val phone by viewModel.phone.collectAsState()
    val location by viewModel.location.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val website by viewModel.website.collectAsState()
    val websiteApproved by viewModel.websiteApproved.collectAsState()
    val dealerAddress by viewModel.dealerAddress.collectAsState()
    val dealerUid by viewModel.dealerUid.collectAsState()
    val isDealer by viewModel.isDealer.collectAsState()
    val whatsapp by viewModel.whatsapp.collectAsState()
    val emailNotifications by viewModel.emailNotifications.collectAsState()
    val newsletter by viewModel.newsletter.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil bearbeiten", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !isSaving && name.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Speichern", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Avatar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        if (avatar != null) {
                            val avatarUrl = avatar!!.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    name.take(1).uppercase(),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Profilbild", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }

                // Name
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Name", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { viewModel.setName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            placeholder = { Text("Dein Name") }
                        )
                    }
                }

                // Bio
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Über mich", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Text("${bio.length}/500", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 500) viewModel.setBio(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = { Text("Erzähl etwas über dich...") }
                        )
                    }
                }

                // Phone
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Telefonnummer (optional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { viewModel.setPhone(it) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            placeholder = { Text("+43 ...") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }

                // Location
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Standort (optional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = location,
                            onValueChange = { viewModel.setLocation(it) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            placeholder = { Text("z.B. Wien, Linz, Graz ...") },
                            leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp)) }
                        )
                    }
                }

                // Website — public only after moderation, same as on the website.
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Website (optional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            if (website.isNotBlank()) {
                                Surface(
                                    color = if (websiteApproved) Color(0xFF10B981).copy(0.12f) else Color(0xFFF59E0B).copy(0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        if (websiteApproved) "✓ Freigegeben" else "In Prüfung",
                                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = if (websiteApproved) Color(0xFF059669) else Color(0xFFD97706),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = website,
                            onValueChange = { viewModel.setWebsite(it) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            placeholder = { Text("https://www.deine-website.at") },
                            leadingIcon = { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Text(
                            "Nach jeder Änderung wird die Website erneut geprüft, bevor sie öffentlich sichtbar ist.",
                            fontSize = 11.sp, lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                if (isDealer) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Firmendaten", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = dealerAddress,
                                onValueChange = { viewModel.setDealerAddress(it) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                placeholder = { Text("Musterstraße 1, 4020 Linz") },
                                leadingIcon = { Icon(Icons.Filled.Business, null, Modifier.size(18.dp)) },
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = dealerUid,
                                onValueChange = { viewModel.setDealerUid(it) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                placeholder = { Text("UID-Nummer, z.B. ATU12345678") },
                                leadingIcon = { Icon(Icons.Filled.Badge, null, Modifier.size(18.dp)) },
                            )
                        }
                    }
                }

                // Notification preferences
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("Benachrichtigungen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SettingsToggle("WhatsApp-Kontakt", "Käufer dürfen dich per WhatsApp anschreiben",
                            whatsapp) { viewModel.setWhatsapp(it) }
                        SettingsToggle("E-Mail-Benachrichtigungen", "Nachrichten und Anfragen per E-Mail",
                            emailNotifications) { viewModel.setEmailNotifications(it) }
                        SettingsToggle("Newsletter", "Tipps und Neuigkeiten von Nimmdas",
                            newsletter) { viewModel.setNewsletter(it) }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
