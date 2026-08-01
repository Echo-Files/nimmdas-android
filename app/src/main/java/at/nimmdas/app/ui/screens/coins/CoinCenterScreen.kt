package at.nimmdas.app.ui.screens.coins

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.*
import at.nimmdas.app.data.i18n.AppTranslations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CoinViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient
    private val _data = MutableStateFlow<CoinData?>(null)
    val data = _data.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _actionLoading = MutableStateFlow(false)
    val actionLoading = _actionLoading.asStateFlow()

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings = _listings.asStateFlow()
    private val _listingsLoading = MutableStateFlow(false)
    val listingsLoading = _listingsLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    init { load() }

    fun load() { viewModelScope.launch {
        try {
            val r = apiClient.api.getCoins()
            if (r.isSuccessful) {
                _data.value = r.body()
                _loadError.value = null
            } else {
                // Without this the screen would sit on its spinner forever.
                _loadError.value = "Münz-Center konnte nicht geladen werden (${r.code()})"
            }
        } catch (_: Exception) {
            _loadError.value = "Keine Verbindung zum Server"
        }
    }}

    fun loadListings() { viewModelScope.launch {
        _listingsLoading.value = true
        try {
            val r = apiClient.api.getMyListings()
            if (r.isSuccessful) {
                _listings.value = r.body()?.filter { it.status == "active" } ?: emptyList()
            }
        } catch (_: Exception) {}
        _listingsLoading.value = false
    }}

    fun doAction(action: String, choice: String? = null, listingId: String? = null) { viewModelScope.launch {
        _actionLoading.value = true; _message.value = null
        try {
            val r = apiClient.api.coinAction(CoinActionRequest(action = action, choice = choice, listingId = listingId))
            val res = r.body()
            if (r.isSuccessful && res != null) {
                if (action.startsWith("shop_")) {
                    if (res.success) {
                        _message.value = "buy_success"
                        load()
                    } else {
                        _message.value = res.error ?: "buy_error"
                    }
                } else {
                    val reward = res.reward ?: res.earned ?: 0
                    _message.value = when {
                        res.isWin == true -> "🎉 Gewonnen! +${reward} Münzen"
                        res.isWin == false -> "😔 Leider verloren"
                        reward > 0 -> "🎉 +${reward} Münzen!"
                        else -> "Kein Gewinn diesmal"
                    }
                    load()
                }
            } else { 
                _message.value = if (action.startsWith("shop_")) "buy_error" else "Fehler: ${r.code()}"
            }
        } catch (e: Exception) { 
            _message.value = if (action.startsWith("shop_")) "buy_error" else "Netzwerkfehler" 
        }
        _actionLoading.value = false
    }}
}

/** Badge catalogue, mirroring the website's coin center. */
private data class Achievement(val name: String, val reward: Int, val icon: String)

private val ACHIEVEMENTS = listOf(
    Achievement("Erster Verkauf", 50, "🛍️"),
    Achievement("Trusted Seller", 100, "⭐"),
    Achievement("Fotoprofi", 20, "📸"),
    Achievement("Power Seller", 200, "💎"),
    Achievement("Community-Held", 75, "🤝"),
    Achievement("Streak Master", 200, "🔥"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinCenterScreen(
    onBack: () -> Unit,
    onWebPageClick: (String, String) -> Unit = { _, _ -> },
    vm: CoinViewModel = viewModel()
) {
    val data by vm.data.collectAsState()
    val message by vm.message.collectAsState()
    val loading by vm.actionLoading.collectAsState()
    val loadError by vm.loadError.collectAsState()
    var activeTab by remember { mutableStateOf("overview") }

    val context = androidx.compose.ui.platform.LocalContext.current
    var loc by remember { mutableStateOf("de") }
    LaunchedEffect(Unit) {
        loc = (context.applicationContext as NimmdasApp).apiClient.getLocale()
    }

    var showListingPickerDialog by remember { mutableStateOf(false) }
    var pendingPurchaseAction by remember { mutableStateOf<String?>(null) }
    var pendingPurchasePrice by remember { mutableStateOf(0) }
    val listings by vm.listings.collectAsState()
    val listingsLoading by vm.listingsLoading.collectAsState()

    // Dialog trigger
    if (showListingPickerDialog && pendingPurchaseAction != null) {
        AlertDialog(
            onDismissRequest = { showListingPickerDialog = false; pendingPurchaseAction = null },
            title = { Text(AppTranslations.t("select_listing", loc), fontWeight = FontWeight.Bold) },
            text = {
                if (listingsLoading) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (listings.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text(AppTranslations.t("no_active_listings", loc), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listings) { listing ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.doAction(pendingPurchaseAction!!, listingId = listing.id)
                                        showListingPickerDialog = false
                                        pendingPurchaseAction = null
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(listing.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        listing.price?.let {
                                            Text("€ ${it.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showListingPickerDialog = false; pendingPurchaseAction = null }) {
                    Text("Schließen")
                }
            }
        )
    }

    val isDark = isSystemInDarkTheme()
    val ambientGreenGlow = if (isDark) Color(0x0C00BC7D) else Color(0x0700BC7D)
    val ambientBlueGlow = if (isDark) Color(0x0C009688) else Color(0x06009688)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("🪙 Münz-Center", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
        )
    }) { padding ->
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (loadError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(loadError!!, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.load() }) { Text("Erneut versuchen") }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }
        val d = data!!

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    // Draw a soft ambient glowing circle in the top right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientGreenGlow, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.12f),
                            radius = size.width * 0.7f
                        ),
                        radius = size.width * 0.7f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.12f)
                    )
                    // Draw a soft ambient glowing circle in the bottom left
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientBlueGlow, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.65f),
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.65f)
                    )
                }
        ) {
            // ── Balance Header ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0x0D00BC7D), Color.Transparent)))
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val greenGradient = Brush.linearGradient(listOf(Color(0xFF00BC7D), Color(0xFF00D49A)))
                        val coralGradient = Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800)))
                        
                        BalanceChip("🪙 ${d.coins}", "Münzen", greenGradient)
                        BalanceChip("🔥 ${d.loginStreak}", "Streak", coralGradient)
                    }
                    
                    // Top-Up Action Button
                    Button(
                        onClick = { onWebPageClick("${BuildConfig.API_BASE_URL}/kaufen", "Münzen aufladen") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFFFC107), Color(0xFFFFB300))))
                            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Aufladen", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                    }
                }
            }

            // ── Tab Bar ──
            val tabs = listOf(
                "overview" to "🏠 Übersicht",
                "shop" to (AppTranslations.t("coin_shop", loc).ifBlank { "🛒 Shop" }),
                "treasure" to "🎁 Schatz",
                "slot" to "🎰 Jackpot",
                "memory" to "🧠 Memory",
                "toss" to "🪙 Wurf",
                "spin" to "🎡 Rad",
                "scratch" to "🎫 Los",
                "history" to "📊 Verlauf"
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs) { (id, label) ->
                    FilterChip(selected = activeTab == id, onClick = { activeTab = id },
                        label = { Text(label, fontSize = 13.sp) },
                        shape = RoundedCornerShape(20.dp))
                }
            }

            // ── Message ──
            AnimatedVisibility(message != null) {
                val displayMessage = message?.let {
                    if (it == "buy_success" || it == "buy_error" || it == "no_active_listings") {
                        AppTranslations.t(it, loc)
                    } else {
                        it
                    }
                } ?: ""
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(displayMessage, Modifier.padding(14.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // ── Content ──
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (activeTab) {
                    "overview" -> {
                        // Daily Claim
                        GameCard("📅 Täglicher Bonus", if (d.canClaimDaily) "Jetzt abholen!" else "Morgen wieder", d.canClaimDaily && !loading) {
                            vm.doAction("daily")
                        }
                        // Quick Games Grid
                        Text("🎮 Spiele", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        val games = listOf(
                            GameItem("🎁","Schatzsuche","treasure",d.gameStates?.canTreasure == true),
                            GameItem("🎰","Jackpot","slot",d.gameStates?.slotSpinsRemaining ?: 0 > 0),
                            GameItem("🧠","Memory","memory",d.gameStates?.canMemory == true),
                            GameItem("🪙","Münzwurf","toss",d.gameStates?.tossesRemaining ?: 0 > 0),
                            GameItem("🎡","Glücksrad","spin",d.canSpin),
                            GameItem("🎫","Rubbellos","scratch",d.scratchCardsRemaining > 0),
                        )
                        games.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { g ->
                                    Card(
                                        onClick = { activeTab = g.tab },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(g.emoji, fontSize = 28.sp)
                                            Text(g.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                            if (g.available) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                        }
                                    }
                                }
                            }
                        }
                        // Achievements — same six badges as on the website.
                        SectionTitle("🏆 Achievements")
                        Text("Sammle Abzeichen und verdiene Bonus-Münzen", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        ACHIEVEMENTS.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { a ->
                                    Card(
                                        Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
                                    ) {
                                        Column(
                                            Modifier.padding(vertical = 10.dp, horizontal = 6.dp).fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(a.icon, fontSize = 22.sp)
                                            Text(a.name, fontSize = 9.sp, fontWeight = FontWeight.Medium,
                                                maxLines = 1, textAlign = TextAlign.Center)
                                            Text("+${a.reward}🪙", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706))
                                        }
                                    }
                                }
                                // Keep the last row aligned when it isn't full.
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }

                        // How to earn
                        SectionTitle("💡 So verdienst du")
                        listOf("📅 Login" to "+5-15/Tag","🎰 Glücksrad" to "+2-30","🎫 Rubbellos" to "+1-20","📝 Inserat" to "+10").forEach { (a,c) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(a, fontSize = 13.sp); Text(c, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC107))
                            }
                        }

                        // Invite Friends
                        d.referralCode?.let { refCode ->
                            Spacer(Modifier.height(16.dp))
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "Schau dir Nimmdas an! Registriere dich mit meinem Link und starte durch:\nhttps://nimmdas.at/register?ref=$refCode")
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Link teilen")
                                    context.startActivity(shareIntent)
                                },
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.06f))
                            ) {
                                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Freunde einladen", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Teile deinen Link. Wenn sich jemand anmeldet, bekommst du +2500 Münzen!", fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                    }
                                }
                            }
                        }
                    }
                    "shop" -> {
                        CoinShopView(
                            data = d,
                            loc = loc,
                            loading = loading,
                            onPurchaseClick = { action, price, requiresListing ->
                                if (requiresListing) {
                                    pendingPurchaseAction = action
                                    pendingPurchasePrice = price
                                    showListingPickerDialog = true
                                    vm.loadListings()
                                } else {
                                    vm.doAction(action)
                                }
                            }
                        )
                    }
                    "treasure" -> AnimatedTreasureHunt(
                        enabled = d.gameStates?.canTreasure == true,
                        loading = loading,
                        onPlay = { vm.doAction("treasure") }
                    )
                    "slot" -> AnimatedSlotMachine(
                        enabled = d.coins >= 5 && (d.gameStates?.slotSpinsRemaining ?: 0) > 0,
                        loading = loading,
                        onPlay = { vm.doAction("slot") }
                    )
                    "memory" -> AnimatedMemoryMatch(
                        enabled = d.gameStates?.canMemory == true,
                        loading = loading,
                        onPlay = { vm.doAction("memory") }
                    )
                    "toss" -> {
                        AnimatedCoinToss(
                            enabled = d.coins >= 10 && (d.gameStates?.tossesRemaining ?: 0) > 0,
                            loading = loading,
                            onPlay = { choice -> vm.doAction("toss", choice) }
                        )
                        if ((d.gameStates?.tossesRemaining ?: 0) > 0)
                            Text("${d.gameStates?.tossesRemaining} Würfe übrig", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    "spin" -> AnimatedLuckyWheel(
                        enabled = d.canSpin,
                        loading = loading,
                        onPlay = { vm.doAction("spin") }
                    )
                    "scratch" -> {
                        AnimatedScratchCard(
                            enabled = d.scratchCardsRemaining > 0,
                            loading = loading,
                            onPlay = { vm.doAction("scratch") }
                        )
                        Text("${d.scratchCardsRemaining} Lose übrig heute", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    "history" -> {
                        Text("📊 Transaktionsverlauf", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (d.transactions.isEmpty()) {
                            Text("Noch keine Transaktionen", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                        }
                        d.transactions.forEach { tx ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tx.description, fontSize = 13.sp, maxLines = 1)
                                    Text(tx.createdAt?.take(10) ?: "", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                }
                                Text("${if(tx.amount>0)"+" else ""}${tx.amount} 🪙", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    color = if(tx.amount>0) Color(0xFF4CAF50) else Color(0xFFF44336))
                            }
                            HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
fun ActivePerksSection(perks: ActivePerks, loc: String) {
    var hasPerks = false
    val premiumBadgeUntil = perks.premiumBadgeUntil
    val detailStatsUnlocked = perks.detailStatsUnlocked
    val boostedListings = perks.boostedListings
    val bumpedListings = perks.bumpedListings

    if (!premiumBadgeUntil.isNullOrBlank() || detailStatsUnlocked || boostedListings.isNotEmpty() || bumpedListings.isNotEmpty()) {
        hasPerks = true
    }

    if (hasPerks) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = AppTranslations.t("active_perks", loc),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // 🌟 Premium-Badge
                if (!premiumBadgeUntil.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                        Text(
                            text = AppTranslations.t("perk_premium", loc) + " (${premiumBadgeUntil.take(10)})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 📊 Detailstatistiken
                if (detailStatsUnlocked) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.BarChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = AppTranslations.t("perk_stats", loc),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ⚡ Turbo-Boost
                boostedListings.forEach { listing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Bolt, null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp))
                        Text(
                            text = AppTranslations.tFormat("perk_boost", loc, listing.title) + " (${listing.boostedUntil?.take(10) ?: ""})",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 🔝 Hervorgehoben / Bumped
                bumpedListings.forEach { listing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.ArrowUpward, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        Text(
                            text = AppTranslations.tFormat("perk_bump", loc, listing.title) + " (${listing.bumpedAt?.take(10) ?: ""})",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CoinShopView(
    data: CoinData,
    loc: String,
    loading: Boolean,
    onPurchaseClick: (String, Int, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Active Perks Card at the top
        data.activePerks?.let { perks ->
            ActivePerksSection(perks, loc)
        }

        // Shop Header / Description
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = AppTranslations.t("coin_shop", loc),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = AppTranslations.t("shop_desc", loc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Shop items
        val items = listOf(
            ShopItemData("shop_bump", 100, true),
            ShopItemData("shop_highlight", 200, true),
            ShopItemData("shop_premium_badge", 300, false),
            ShopItemData("shop_boost", 500, true),
            ShopItemData("shop_stats", 50, false)
        )

        items.forEach { item ->
            val title = getShopItemText(item.action, "title", loc)
            val desc = getShopItemText(item.action, "desc", loc)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), lineHeight = 16.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1AFFC107))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${item.price} 🪙", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFFB300))
                        }
                        Button(
                            onClick = { onPurchaseClick(item.action, item.price, item.requiresListing) },
                            enabled = !loading && data.coins >= item.price,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00BC7D),
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = if (data.coins >= item.price) "Kaufen" else "Zu wenig",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ShopItemData(val action: String, val price: Int, val requiresListing: Boolean)

fun getShopItemText(action: String, part: String, loc: String): String {
    return when (loc) {
        "en" -> when (action) {
            "shop_highlight" -> if (part == "title") "📌 Highlight Listing" else "Highlight your listing in color for 7 days to stand out in search results."
            "shop_bump" -> if (part == "title") "🔝 Bump to Top" else "Instantly push your listing to the top of search results – as if newly posted."
            "shop_boost" -> if (part == "title") "⚡ Turbo Boost" else "Automatically bump daily and get a premium placement for 7 days."
            "shop_premium_badge" -> if (part == "title") "🌟 Premium Badge" else "Get an exclusive crown icon next to your name for 30 days to build trust."
            "shop_stats" -> if (part == "title") "📊 Detailed Stats" else "Unlock detailed click and visitor statistics for your profile for 30 days."
            else -> ""
        }
        "tr" -> when (action) {
            "shop_highlight" -> if (part == "title") "📌 İlanı Öne Çıkar" else "Arama sonuçlarında dikkat çekmek için ilanınızı 7 gün boyunca renkli olarak işaretleyin."
            "shop_bump" -> if (part == "title") "🔝 Yukarı Taşı" else "İlanınızı arama sonuçlarının en tepesine taşıyın - yeni eklenmiş gibi."
            "shop_boost" -> if (part == "title") "⚡ Turbo Hızlandırma" else "7 gün boyunca otomatik olarak her gün yukarı taşıyın ve premium yerleşim elde edin."
            "shop_premium_badge" -> if (part == "title") "🌟 Premium Rozet" else "Güven oluşturmak için 30 gün boyunca adınızın yanında özel bir taç simgesi kazanın."
            "shop_stats" -> if (part == "title") "📊 Detaylı İstatistikler" else "Profiliniz için 30 gün boyunca detaylı tıklama ve ziyaretçi istatistiklerini açın."
            else -> ""
        }
        else -> when (action) {
            "shop_highlight" -> if (part == "title") "📌 Inserat hervorheben" else "Markiere dein Inserat 7 Tage lang farbig, um in den Suchergebnissen hervorzustechen."
            "shop_bump" -> if (part == "title") "🔝 Inserat nach oben" else "Pushe dein Inserat sofort an die Spitze der Suchergebnisse – wie neu eingestellt."
            "shop_boost" -> if (part == "title") "⚡ Turbo-Boost" else "Automatisch täglich nach oben pushen und 7 Tage lang Premium-Platzierung erhalten."
            "shop_premium_badge" -> if (part == "title") "🌟 Premium-Badge" else "Erhalte ein exklusives Kronen-Symbol neben deinem Namen für 30 Tage und stärke das Vertrauen."
            "shop_stats" -> if (part == "title") "📊 Detailstatistiken" else "Schalte detaillierte Klick- und Besucherstatistiken für dein gesamtes Profil für 30 tage frei."
            else -> ""
        }
    }
}


data class GameItem(val emoji: String, val label: String, val tab: String, val available: Boolean)

@Composable
fun BalanceChip(text: String, sub: String, gradient: Brush) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0x1AFFFFFF)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                Text(sub, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun GameCard(title: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            Button(
                onClick = onClick, 
                enabled = enabled, 
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00BC7D),
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if(enabled) "🎮 Spielen" else "❌ Nicht verfügbar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}
