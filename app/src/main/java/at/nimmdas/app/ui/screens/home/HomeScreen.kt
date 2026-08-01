package at.nimmdas.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.data.model.Listing
import at.nimmdas.app.ui.components.ListingCard
import at.nimmdas.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onListingClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    homeViewModel: HomeViewModel = viewModel()
) {
    val listings by homeViewModel.listings.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val userName by homeViewModel.userName.collectAsState()
    val error by homeViewModel.error.collectAsState()

    // Shared saved-listings state so each card can show its own heart.
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as at.nimmdas.app.NimmdasApp
    val savedIds by app.watchlist.savedIds.collectAsState()
    LaunchedEffect(Unit) { app.watchlist.ensureLoaded() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Hero Section with green gradient ──────
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
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                // Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Der smarte Marktplatz", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (userName != null) {
                    Text("Hallo, $userName! 👋",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp))
                    Text("Was suchst du heute?",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Nimmdas",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp))
                    Text("Was du wirklich suchst.",
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }

                // Typewriter effect state
                val placeholders = listOf(
                    "Wonach suchst du?", "iPhone 15 Pro Max...", "Günstige BMW 3er...",
                    "Traumwohnung in Wien...", "Vintage Möbel...", "Jobs in Österreich..."
                )
                var currentIdx by remember { mutableIntStateOf(0) }
                var text by remember { mutableStateOf("") }
                var isDeleting by remember { mutableStateOf(false) }

                LaunchedEffect(text, isDeleting, currentIdx) {
                    val target = placeholders[currentIdx % placeholders.size]
                    if (isDeleting) {
                        if (text.isEmpty()) {
                            isDeleting = false
                            currentIdx++
                            kotlinx.coroutines.delay(500)
                        } else {
                            text = text.dropLast(1)
                            kotlinx.coroutines.delay(40)
                        }
                    } else {
                        if (text == target) {
                            kotlinx.coroutines.delay(2500)
                            isDeleting = true
                        } else {
                            text = target.take(text.length + 1)
                            kotlinx.coroutines.delay(80)
                        }
                    }
                }

                // Search bar
                Surface(
                    onClick = onSearchClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (text.isEmpty()) " " else text,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        // ── Error Banner ─────────────────────────
        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Ladefehler: $error",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ── Quick Categories ──────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            // Label carries an emoji prefix; the value after it is the real category name
            // the search API expects.
            val cats = listOf("🚗 Autos", "📱 Elektronik", "👗 Mode", "🛋️ Möbel", "🏠 Immobilien", "💼 Jobs", "⚽ Sport", "🚲 Fahrräder")
            items(cats) { cat ->
                Surface(
                    onClick = {
                        at.nimmdas.app.ui.screens.search.PendingSearch
                            .requestCategory(cat.substringAfter(' ').trim())
                        onSearchClick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // ── Listing Sections ──────────────────────
        if (isLoading && listings.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        } else {
            // Group by categories
            val categories = listings.groupBy { it.category ?: "Sonstiges" }
                .filter { it.value.size >= 2 }
                .entries.take(5)

            if (categories.isNotEmpty()) {
                categories.forEach { (category, catListings) ->
                    ListingSection(
                        title = getCategoryEmoji(category) + " " + category,
                        listings = catListings.take(10),
                        onListingClick = onListingClick,
                        savedIds = savedIds,
                        onToggleSave = { app.watchlist.toggle(it) },
                    )
                }
            }

            // Latest
            ListingSection(
                title = "🆕 Neueste Inserate",
                listings = listings.take(15),
                onListingClick = onListingClick,
                savedIds = savedIds,
                onToggleSave = { app.watchlist.toggle(it) },
            )

            // Call to action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hast du etwas zu verkaufen?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Inserat in 30 Sekunden erstellen – kostenlos!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White
                    ) {
                        Text(
                            "✨ Jetzt verkaufen",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ListingSection(
    title: String,
    listings: List<Listing>,
    onListingClick: (String) -> Unit,
    savedIds: Set<String> = emptySet(),
    onToggleSave: (String) -> Unit = {},
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listings) { listing ->
                ListingCard(
                    listing = listing,
                    onClick = { onListingClick(listing.id) },
                    isSaved = listing.id in savedIds,
                    onToggleSave = { onToggleSave(listing.id) },
                )
            }
        }
    }
}

private fun getCategoryEmoji(cat: String): String = when (cat) {
    "Autos" -> "🚗"
    "Elektronik" -> "📱"
    "Mode" -> "👗"
    "Möbel" -> "🛋️"
    "Sport" -> "⚽"
    "Immobilien" -> "🏠"
    "Jobs" -> "💼"
    "Dienstleistungen" -> "🔧"
    "Fahrräder" -> "🚲"
    "Haushalt" -> "🏡"
    else -> "📦"
}
