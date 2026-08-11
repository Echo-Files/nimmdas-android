package at.nimmdas.app.ui.screens.profile

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import at.nimmdas.app.data.model.Listing
import at.nimmdas.app.data.model.UserInfo
import at.nimmdas.app.data.model.UserStats
import at.nimmdas.app.data.model.WatchlistRequest
import at.nimmdas.app.ui.components.ListingCard
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import at.nimmdas.app.data.i18n.AppTranslations.t
import at.nimmdas.app.data.i18n.AppTranslations.tFormat

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _user = MutableStateFlow<UserInfo?>(null)
    val user = _user.asStateFlow()

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats = _stats.asStateFlow()

    private val _myListings = MutableStateFlow<List<Listing>>(emptyList())
    val myListings = _myListings.asStateFlow()

    private val _savedListings = MutableStateFlow<List<Listing>>(emptyList())
    val savedListings = _savedListings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val meResponse = apiClient.api.getMe()
                if (meResponse.isSuccessful) {
                    _user.value = meResponse.body()?.user
                    _stats.value = meResponse.body()?.stats
                }
                val listingsResponse = apiClient.api.getMyListings()
                if (listingsResponse.isSuccessful) {
                    _myListings.value = listingsResponse.body() ?: emptyList()
                }
                // Load watchlist
                try {
                    val wlResponse = apiClient.api.getWatchlist()
                    if (wlResponse.isSuccessful) {
                        _savedListings.value = wlResponse.body()?.savedListings ?: emptyList()
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun deleteListing(id: String) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.deleteListing(id)
                if (res.isSuccessful) {
                    _myListings.value = _myListings.value.filter { it.id != id }
                }
            } catch (_: Exception) {}
        }
    }

    fun updateListingStatus(id: String, status: String) {
        viewModelScope.launch {
            try {
                val req = at.nimmdas.app.data.model.StatusUpdateRequest(status)
                val res = apiClient.api.updateListingStatus(id, req)
                if (res.isSuccessful) {
                    loadProfile()
                }
            } catch (_: Exception) {}
        }
    }

    private val _bumpMessage = MutableStateFlow<String?>(null)
    val bumpMessage = _bumpMessage.asStateFlow()

    fun bumpListing(id: String) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.bumpListing(id)
                if (res.isSuccessful) {
                    _bumpMessage.value = "\u2705 Inserat nach oben geschoben!"
                } else {
                    _bumpMessage.value = "\u23f3 Nur alle 24h m\u00f6glich"
                }
            } catch (_: Exception) {
                _bumpMessage.value = "Fehler beim Pushen"
            }
        }
    }

    fun clearBumpMessage() { _bumpMessage.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onListingClick: (String) -> Unit,
    onCoinsClick: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onEditListing: (String) -> Unit = {},
    onSavedSearchesClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onDraftsClick: () -> Unit = {},
    onWebPageClick: (String, String) -> Unit = { _, _ -> },
    viewModel: ProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsState()
    val myListings by viewModel.myListings.collectAsState()
    val savedListings by viewModel.savedListings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val bumpMessage by viewModel.bumpMessage.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLangDialog by remember { mutableStateOf(false) }
    var loc by remember { mutableStateOf("de") }

    LaunchedEffect(Unit) {
        loc = (context.applicationContext as NimmdasApp).apiClient.getLocale()
    }

    LaunchedEffect(bumpMessage) {
        bumpMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBumpMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            // No TopAppBar here, so this screen carries the status-bar inset itself.
            .statusBarsPadding()
    ) {
        if (isLoading && user == null) {
            Box(modifier = Modifier.fillMaxSize().padding(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
            return
        }

        // ── Profile Header with gradient ──────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Avatar
                Box(modifier = Modifier.size(72.dp)) {
                    if (user?.avatar != null) {
                        AsyncImage(
                            model = user!!.avatar,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    (user?.name?.take(1) ?: "").uppercase().ifBlank { "?" },
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Name & email
                Text(
                    user?.name ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    user?.email ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(16.dp))

                // ── Stats row ─────────────────────
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                                        StatItem("${myListings.size}", t("listings", loc))
                        VerticalDivider()
                        StatItem("${myListings.sumOf { it.views ?: 0 }}", t("views", loc))
                        VerticalDivider()
                        Box(Modifier.clickable { onCoinsClick() }) {
                            StatItem("${user?.coins ?: 0}", t("coins", loc), color = Color(0xFFD97706))
                        }
                    }
                }

                // Münz-Center Button
                Card(
                    onClick = onCoinsClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1AFFC107)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🪙", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("coin_center", loc), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(if (loc == "de") "Spiele, Shop & Streak" else if (loc == "tr") "Oyunlar, Dükkan & Seri" else "Games, Shop & Streak", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Edit & Logout
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Settings, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("edit", loc), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("logout", loc), fontSize = 13.sp)
                    }
                }
            }
        }

        // ── Menu Items ────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                ProfileMenuItem(Icons.Filled.BarChart, t("stats", loc), if (loc == "de") "Aufrufe & Performance" else if (loc == "tr") "Görüntülenme ve Performans" else "Views & Performance", MaterialTheme.colorScheme.primary) { onStatsClick() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Description, t("drafts", loc), if (loc == "de") "Gespeicherte Entwürfe" else if (loc == "tr") "Taslaklar" else "Saved drafts", MaterialTheme.colorScheme.primary) { onDraftsClick() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                // Import runs through the authenticated web view — it is a multi-step
                // upload flow that has no mobile endpoint.
                ProfileMenuItem(Icons.Filled.CloudUpload, if (loc == "de") "Plattform-Import" else if (loc == "tr") "Platform içe aktarma" else "Platform import", if (loc == "de") "Inserate importieren" else if (loc == "tr") "İlanları içe aktar" else "Import listings", Color(0xFF3B82F6)) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/profile/import/willhaben", if (loc == "de") "Plattform-Import" else "Platform import")
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Notifications, t("saved_searches", loc), if (loc == "de") "Benachrichtigungen" else if (loc == "tr") "Arama bildirimleri" else "Alert notifications", MaterialTheme.colorScheme.primary) { onSavedSearchesClick() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.MonetizationOn, t("coin_center", loc), if (loc == "de") "Münzen verdienen" else if (loc == "tr") "Coin kazan" else "Earn coins", Color(0xFFD97706)) { onCoinsClick() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Translate, t("select_language", loc), if (loc == "de") "Deutsch 🇩🇪" else if (loc == "tr") "Türkçe 🇹🇷" else "English 🇬🇧", Color(0xFF8B5CF6)) { showLangDialog = true }
                if (user?.role == "admin" || user?.role == "mod") {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ProfileMenuItem(Icons.Filled.AdminPanelSettings, if (loc == "de") "Admin-Bereich" else if (loc == "tr") "Yönetici Paneli" else "Admin Area", if (loc == "de") "System verwalten" else if (loc == "tr") "Sistemi yönet" else "Manage system", Color(0xFFEF4444)) {
                        onWebPageClick("${BuildConfig.API_BASE_URL}/admin", if (loc == "de") "Admin-Bereich" else "Admin Area")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Help & Information Menu Card ────────────────────────────
        Text(t("help_info", loc).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), letterSpacing = 1.sp)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                ProfileMenuItem(Icons.Filled.Book, t("handbook", loc), if (loc == "de") "User-Handbuch" else "User handbook", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/handbuch", t("handbook", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Security, t("safety_tips", loc), if (loc == "de") "Betrug verhindern" else "Prevent fraud", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/sicherheitstipps", t("safety_tips", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.SupportAgent, t("support", loc), if (loc == "de") "Support kontaktieren" else "Contact support", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/support", t("support", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Compare, t("alternatives", loc), if (loc == "de") "Willhaben Alternativen" else "Flea market alternatives", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/alternative", t("alternatives", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Folder, t("directory", loc), if (loc == "de") "Händler-Verzeichnis" else "Merchant directory", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/verzeichnis", t("directory", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.DirectionsCar, t("merchant_area", loc), if (loc == "de") "Händlerbereich / API" else "Merchant area / API", Color(0xFF059669)) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/api-docs", t("merchant_area", loc))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Legal Menu Card ────────────────────────────
        Text(t("legal", loc).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), letterSpacing = 1.sp)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                ProfileMenuItem(Icons.Filled.Gavel, t("terms", loc), if (loc == "de") "Allgemeine Geschäftsbedingungen" else "Terms of service", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/agb", t("terms", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.PrivacyTip, t("privacy", loc), if (loc == "de") "Datenschutzerklärung" else "Privacy policy", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/datenschutz", t("privacy", loc))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ProfileMenuItem(Icons.Filled.Info, t("imprint", loc), if (loc == "de") "Impressum" else "Imprint", MaterialTheme.colorScheme.primary) {
                    onWebPageClick("${BuildConfig.API_BASE_URL}/impressum", t("imprint", loc))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Invite card ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t("invite_friends", loc), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (loc == "de") "Sichere dir 2.500 Münzen" else if (loc == "tr") "2.500 Coin kazan" else "Get 2,500 Coins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    t("referral_desc", loc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val refCode = user?.referralCode ?: ""
                        val shareUrl = if (refCode.isNotBlank()) "${BuildConfig.API_BASE_URL}/register?ref=$refCode" else "${BuildConfig.API_BASE_URL}/register"
                        val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Nimmdas - " + t("smarter_market", loc))
                            putExtra(Intent.EXTRA_TEXT, if (loc == "de") "Schau dir Nimmdas an – der kostenlose Marktplatz aus Österreich! \uD83C\uDDE6\uD83C\uDDF9\nRegistriere dich über meinen Link und wir bekommen beide 2.500 Münzen! \uD83E\uDE99\n$shareUrl" else "Check out Nimmdas – the smart marketplace! \uD83E\uDD1D\nSign up with my link and we both get 2,500 coins! \uD83E\uDE99\n$shareUrl")
                        }, "Teilen via")
                        context.startActivity(shareIntent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("invite_btn", loc), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Tabs: Inserate / Merkliste ────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // Tab selector
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("📦 " + t("listings", loc) + " (${myListings.size})", "❤️ " + t("watchlist", loc) + " (${savedListings.size})").forEachIndexed { idx, label ->
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == idx) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (selectedTab == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Content
                if (selectedTab == 0) {
                    if (myListings.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📦", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(t("no_listings", loc), fontWeight = FontWeight.SemiBold)
                            Text(t("create_first", loc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { }, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("create_btn", loc), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            myListings.forEach { listing ->
                                ProfileListingCard(
                                    listing = listing,
                                    onClick = { onListingClick(listing.id) },
                                    onEdit = { onEditListing(listing.id) },
                                    onStatusChange = { newStatus ->
                                        viewModel.updateListingStatus(listing.id, newStatus)
                                    },
                                    onDelete = { viewModel.deleteListing(listing.id) },
                                    onBump = { viewModel.bumpListing(listing.id) }
                                )
                            }
                        }
                    }
                } else {
                    if (savedListings.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("❤️", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(if (loc == "de") "Merkliste leer" else if (loc == "tr") "Favori ilan yok" else "Watchlist is empty", fontWeight = FontWeight.SemiBold)
                            Text(if (loc == "de") "Speichere Inserate mit dem ♡ Button" else if (loc == "tr") "İlanları ♡ butonu ile kaydet" else "Save listings with the ♡ button", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            savedListings.forEach { listing ->
                                ListingCard(
                                    listing = listing,
                                    onClick = { onListingClick(listing.id) },
                                    viewMode = "list"
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Member since ──────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (loc == "de") "MITGLIED SEIT" else if (loc == "tr") "ÜYELİK TARİHİ" else "MEMBER SINCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    user?.location ?: (if (loc == "de") "Nimmdas Mitglied" else if (loc == "tr") "Nimmdas Üyesi" else "Nimmdas Member"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        // Clears the floating nav capsule the content now scrolls behind.
        Spacer(Modifier.height(at.nimmdas.app.navigation.BottomBarSpace))
    }

    if (showLangDialog) {
        val localesMap = mapOf(
            "de" to "Deutsch 🇩🇪",
            "en" to "English 🇬🇧",
            "fr" to "Français 🇫🇷",
            "es" to "Español 🇪🇸",
            "it" to "Italiano 🇮🇹",
            "tr" to "Türkçe 🇹🇷",
            "cs" to "Čeština 🇨🇿",
            "hu" to "Magyar 🇭🇺",
            "hr" to "Hrvatski 🇭🇷",
            "pl" to "Polski 🇵🇱",
            "sr" to "Srpski 🇷🇸"
        )
        
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(t("select_language", loc), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    localesMap.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        (context.applicationContext as NimmdasApp).apiClient.saveLocale(code)
                                        loc = code
                                        viewModel.loadProfile()
                                        showLangDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (loc == code),
                                onClick = {
                                    scope.launch {
                                        (context.applicationContext as NimmdasApp).apiClient.saveLocale(code)
                                        loc = code
                                        viewModel.loadProfile()
                                        showLangDialog = false
                                    }
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    )
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, title: String, subtitle: String, iconColor: Color, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconColor)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
        Icon(
            Icons.Filled.ChevronRight,
            null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        )
    }
}

@Composable
private fun ProfileListingCard(
    listing: Listing,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    onBump: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val imageUrl = listing.images?.firstOrNull()?.let {
        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
    }

    val priceText = when (listing.priceType) {
        "verschenken" -> "Gratis"
        "auf_anfrage" -> "Auf Anfrage"
        "vb" -> "€ ${at.nimmdas.app.ui.components.formatPrice(listing.price)} VB"
        else -> if (listing.price != null && listing.price > 0) "€ ${at.nimmdas.app.ui.components.formatPrice(listing.price)}" else "Gratis"
    }

    val condBadge = when (listing.condition) {
        "neu" -> "Neu" to Color(0xFF10B981)
        "gebraucht" -> "Gebraucht" to Color(0xFF3B82F6)
        "defekt" -> "Defekt" to Color(0xFFEF4444)
        else -> null
    }

    val isNew = listing.createdAt?.let {
        try { System.currentTimeMillis() - at.nimmdas.app.ui.components.parseDate(it) < 86400000 } catch (_: Exception) { false }
    } ?: false

    val isSold = listing.status == "sold"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Image Left Side
            Box(
                Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = listing.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) { Text("📷", fontSize = 28.sp) }
                }

                // Badges
                Column(Modifier.align(Alignment.TopStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isNew) {
                        Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(6.dp)) {
                            Text("NEU", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val statusBadge = when (listing.status) {
                        "sold" -> "VERKAUFT" to Color(0xFF3B82F6)
                        "reserved" -> "RESERVIERT" to Color(0xFFF59E0B)
                        "paused" -> "PAUSIERT" to Color(0xFF6B7280)
                        "draft" -> "ENTWURF" to Color(0xFF8B5CF6)
                        else -> null
                    }
                    statusBadge?.let { (label, color) ->
                        Surface(color = color, shape = RoundedCornerShape(6.dp)) {
                            Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Heart
                Icon(
                    Icons.Filled.FavoriteBorder,
                    null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                    tint = Color.White
                )

                // Image count
                val imageCount = listing.images?.size ?: 0
                if (imageCount > 1) {
                    Surface(
                        color = Color.Black.copy(0.7f), shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    ) {
                        Text("$imageCount Bilder", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Right Side Content
            Column(Modifier.weight(0.6f).padding(12.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(priceText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF10B981))
                        condBadge?.let { (label, color) ->
                            Surface(color = color.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                                Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(listing.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    
                    if (listing.shipping == true) {
                        Spacer(Modifier.height(4.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                            Text("Versand", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        listing.location?.let { loc ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Spacer(Modifier.width(2.dp))
                                Text(loc, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                        listing.createdAt?.let {
                            Text(at.nimmdas.app.ui.components.timeAgo(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👁", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("${listing.views ?: 0}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        // Traffic split, once the detail statistics are unlocked.
                        listing.trafficSources?.takeIf { it.total() > 0 }?.let { ts ->
                            Text("🔎 ${ts.search + ts.google}", fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Text("🔗 ${ts.direct + ts.social + ts.other}", fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onEdit,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF10B981).copy(alpha = 0.1f)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Edit, null, Modifier.size(12.dp), tint = Color(0xFF10B981))
                                Spacer(Modifier.width(4.dp))
                                Text("Bearbeiten", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                        if (listing.status == "active") {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                onClick = onBump,
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF3B82F6).copy(alpha = 0.1f)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ArrowUpward, null, Modifier.size(12.dp), tint = Color(0xFF3B82F6))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pushen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var statusMenu by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { statusMenu = true },
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    when (listing.status) {
                                        "sold" -> "Verkauft"
                                        "reserved" -> "Reserviert"
                                        "paused" -> "Pausiert"
                                        else -> "Aktiv"
                                    } + " ändern",
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                                listOf(
                                    "active" to "✅ Aktiv – öffentlich sichtbar",
                                    "reserved" to "🕒 Reserviert – bleibt sichtbar mit Badge",
                                    "sold" to "💰 Als verkauft markieren",
                                    "paused" to "⏸️ Pausiert – wird deaktiviert",
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = { statusMenu = false; onStatusChange(value) },
                                        enabled = listing.status != value,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp), tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}
