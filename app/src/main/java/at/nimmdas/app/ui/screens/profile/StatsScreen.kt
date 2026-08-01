package at.nimmdas.app.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import at.nimmdas.app.data.model.UserStats
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _stats = MutableStateFlow<UserStats?>(null)
    val stats = _stats.asStateFlow()
    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings = _listings.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    /** Traffic sources are a paid perk — bought once in the coin center. */
    private val _detailUnlocked = MutableStateFlow(false)
    val detailUnlocked = _detailUnlocked.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val meRes = apiClient.api.getMe()
                if (meRes.isSuccessful) {
                    _stats.value = meRes.body()?.stats
                    _detailUnlocked.value = meRes.body()?.user?.detailStatsUnlocked == true
                }
                val listRes = apiClient.api.getMyListings()
                if (listRes.isSuccessful) {
                    _listings.value = listRes.body() ?: emptyList()
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onListingClick: (String) -> Unit = {},
    onUnlockClick: () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val listings by viewModel.listings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val detailUnlocked by viewModel.detailUnlocked.collectAsState()

    val totalListings = listings.size
    val activeListings = listings.count { it.status == "active" }
    val soldListings = listings.count { it.status == "sold" }
    val totalViews = listings.sumOf { it.views ?: 0 }
    val totalRevenue = listings.filter { it.status == "sold" }.sumOf { (it.price ?: 0.0).toInt() }
    val avgViews = if (totalListings > 0) totalViews / totalListings else 0

    val topListings = listings.sortedByDescending { it.views ?: 0 }.take(10)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meine Statistiken", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stats Cards Grid
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatsCard("Inserate", totalListings.toString(), Icons.Filled.ShoppingBag, Color(0xFF3B82F6), Modifier.weight(1f))
                        StatsCard("Aktiv", activeListings.toString(), Icons.Filled.TrendingUp, Color(0xFF10B981), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatsCard("Verkauft", soldListings.toString(), Icons.Filled.AttachMoney, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatsCard("Aufrufe", totalViews.toString(), Icons.Filled.Visibility, Color(0xFFF59E0B), Modifier.weight(1f))
                    }
                }

                // Summary Cards
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Ø Aufrufe", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text("$avgViews", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("pro Inserat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Umsatz", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                Text("€ $totalRevenue", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("verkaufte Artikel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                    }
                }

                // Top Listings
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Top Inserate nach Aufrufen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                if (topListings.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Noch keine Inserate vorhanden.", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                    }
                } else {
                    itemsIndexed(topListings) { index, listing ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(0.5.dp),
                            modifier = Modifier.clickable { onListingClick(listing.id) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank
                                Text(
                                    "${index + 1}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                    modifier = Modifier.width(28.dp)
                                )

                                // Image
                                val imgUrl = listing.images?.firstOrNull()?.let {
                                    if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                                }
                                if (imgUrl != null) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                // Title + details
                                Column(Modifier.weight(1f)) {
                                    Text(listing.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "€${listing.price?.toInt() ?: 0} · ${listing.category ?: ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }

                                // Views
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${listing.views ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Aufrufe", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                }
                            }
                            TrafficSourcesBlock(listing.trafficSources, detailUnlocked, onUnlockClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Per-listing traffic breakdown. Without the unlock it shows the same teaser as the
 * website: the bars stay at zero and a card offers the 50-coin unlock.
 */
@Composable
private fun TrafficSourcesBlock(
    sources: at.nimmdas.app.data.model.TrafficSources?,
    unlocked: Boolean,
    onUnlockClick: () -> Unit,
) {
    val rows = listOf(
        Triple("💻 Direkt / Lesezeichen", sources?.direct ?: 0, Color(0xFF3B82F6)),
        Triple("🔎 Suche (Intern)", sources?.search ?: 0, Color(0xFF10B981)),
        Triple("🔍 Suchmaschinen", sources?.google ?: 0, Color(0xFF8B5CF6)),
        Triple("📱 Social Media", sources?.social ?: 0, Color(0xFFEC4899)),
        Triple("🌐 Andere Verweise", sources?.other ?: 0, Color(0xFF9CA3AF)),
    )
    val total = (sources?.total() ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text("TRAFFIC-QUELLEN & REFERRER", fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        Spacer(Modifier.height(8.dp))
        if (unlocked) {
            rows.forEach { (label, value, color) ->
                val pct = value * 100 / total
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 10.sp, modifier = Modifier.width(130.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp)).background(color))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$value", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(30.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        } else {
            Surface(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF43F5E).copy(0.07f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("📊 Detailstatistiken gesperrt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Schalte Traffic-Quellen & Referrer dauerhaft frei!", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF43F5E)) {
                        Text("50 🪙", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = color.copy(0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = color)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        }
    }
}
