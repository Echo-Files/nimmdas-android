package at.nimmdas.app.ui.screens.search

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import at.nimmdas.app.data.model.*
import at.nimmdas.app.data.i18n.AppTranslations
import at.nimmdas.app.ui.components.ClusterMap
import at.nimmdas.app.ui.components.ListingCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _results = MutableStateFlow<List<Listing>>(emptyList())
    val results = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _totalResults = MutableStateFlow(0)
    val totalResults = _totalResults.asStateFlow()
    /** Endless scrolling — page currently loaded, and whether another one exists. */
    private val _page = MutableStateFlow(1)
    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()
    private val _selectedSort = MutableStateFlow("newest")
    val selectedSort = _selectedSort.asStateFlow()

    // All filter values stored as a map
    private val _filters = MutableStateFlow<MutableMap<String, String>>(mutableMapOf())
    val filters = _filters.asStateFlow()

    // NLP-extracted filter labels for pills
    private val _nlpPills = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val nlpPills = _nlpPills.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    // Live preview shown under the search field while typing
    private val _preview = MutableStateFlow<List<SearchPreviewItem>>(emptyList())
    val preview = _preview.asStateFlow()
    private val _showPreview = MutableStateFlow(false)
    val showPreview = _showPreview.asStateFlow()

    fun loadPreview(q: String) { viewModelScope.launch {
        if (q.trim().length < 2) { _preview.value = emptyList(); _showPreview.value = false; return@launch }
        try {
            val r = apiClient.api.searchPreview(q.trim())
            if (r.isSuccessful) {
                _preview.value = r.body()?.results ?: emptyList()
                _showPreview.value = _preview.value.isNotEmpty()
            }
        } catch (_: Exception) { /* preview is a nicety — stay silent */ }
    }}

    fun dismissPreview() { _showPreview.value = false }

    // Location autocomplete for the "Ort / PLZ" filter
    private val _locationSuggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val locationSuggestions = _locationSuggestions.asStateFlow()

    fun loadLocationSuggestions(q: String) { viewModelScope.launch {
        if (q.trim().length < 2) { _locationSuggestions.value = emptyList(); return@launch }
        try {
            val r = apiClient.api.locationAutocomplete(q.trim())
            if (r.isSuccessful) _locationSuggestions.value = r.body() ?: emptyList()
        } catch (_: Exception) { /* suggestions are optional */ }
    }}

    fun clearLocationSuggestions() { _locationSuggestions.value = emptyList() }

    // Map pins for the current filter set — loaded lazily, only when the map is opened.
    private val _mapPins = MutableStateFlow<List<MapPin>>(emptyList())
    val mapPins = _mapPins.asStateFlow()
    private val _mapLoading = MutableStateFlow(false)
    val mapLoading = _mapLoading.asStateFlow()

    fun loadMapPins() { viewModelScope.launch {
        _mapLoading.value = true
        try {
            val f = _filters.value
            val r = apiClient.api.searchMapPins(
                query = (cleanedQuery ?: _query.value).ifBlank { null },
                category = _selectedCategory.value,
                condition = f["condition"], minPrice = f["minPrice"], maxPrice = f["maxPrice"],
                location = f["location"], radius = f["radius"],
                propertyType = f["propertyType"],
                roomsMin = f["roomsMin"], roomsMax = f["roomsMax"],
                sqmMin = f["sqmMin"], sqmMax = f["sqmMax"],
                brand = f["brand"],
            )
            if (r.isSuccessful) _mapPins.value = r.body()?.listings.orEmpty()
        } catch (_: Exception) { /* map is a secondary view */ }
        _mapLoading.value = false
    }}

    /** "Auf gut Glück" — jumps straight to a random matching listing. */
    fun lucky(onFound: (String) -> Unit, onNone: () -> Unit) { viewModelScope.launch {
        try {
            val f = _filters.value
            val r = apiClient.api.lucky(
                query = _query.value.trim().ifBlank { null },
                category = _selectedCategory.value,
                location = f["location"],
                brand = f["brand"],
            )
            val id = r.body()?.id
            if (r.isSuccessful && !id.isNullOrBlank()) onFound(id) else onNone()
        } catch (_: Exception) { onNone() }
    }}

    // Load results straight away — without this the tab opens on a blank screen (the
    // "no results" branch is skipped while the query is still empty). A category picked
    // on the home screen is applied here so the chips actually filter.
    init {
        _selectedCategory.value = PendingSearch.consumeCategory()
        search()
    }

    /** NLP-stripped version of the query — used for searching, never shown in the field. */
    private var cleanedQuery: String? = null

    fun setQuery(q: String) {
        _query.value = q
        cleanedQuery = null // the user changed the text; the old cleaned term is stale
    }
    fun setCategory(cat: String?) {
        _selectedCategory.value = cat
        // Clear category-specific filters when switching
        _filters.value = mutableMapOf<String, String>().apply {
            _filters.value["minPrice"]?.let { put("minPrice", it) }
            _filters.value["maxPrice"]?.let { put("maxPrice", it) }
            _filters.value["location"]?.let { put("location", it) }
            _filters.value["condition"]?.let { put("condition", it) }
        }
        search()
    }
    fun setSort(s: String) { _selectedSort.value = s; search() }
    fun setFilter(key: String, value: String) {
        _filters.value = _filters.value.toMutableMap().apply {
            if (value.isBlank()) remove(key) else put(key, value)
        }
    }
    fun removeFilter(key: String) {
        _filters.value = _filters.value.toMutableMap().apply { remove(key) }
        _nlpPills.value = _nlpPills.value.filter { it.first != key }
        search()
    }
    fun clearAllFilters() {
        _filters.value = mutableMapOf()
        _nlpPills.value = emptyList()
        _selectedCategory.value = null
        _selectedSort.value = "newest"
        cleanedQuery = null
        search()
    }

    fun searchWithNlp() {
        val q = _query.value.trim()
        if (q.isBlank()) { search(); return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = apiClient.api.smartIntent(q)
                if (resp.isSuccessful) {
                    val r = resp.body() ?: run { search(); return@launch }
                    val pills = mutableListOf<Pair<String, String>>()
                    val newFilters = _filters.value.toMutableMap()

                    r.category?.let { _selectedCategory.value = it; pills.add("category" to it) }
                    r.brand?.let { newFilters["brand"] = it; pills.add("brand" to "Marke: $it") }
                    r.model?.let { newFilters["model"] = it; pills.add("model" to "Modell: $it") }
                    r.minPrice?.let { newFilters["minPrice"] = it.toInt().toString(); pills.add("minPrice" to "ab €${it.toInt()}") }
                    r.maxPrice?.let { newFilters["maxPrice"] = it.toInt().toString(); pills.add("maxPrice" to "bis €${it.toInt()}") }
                    r.location?.let { newFilters["location"] = it; pills.add("location" to "📍 $it") }
                    r.condition?.let { newFilters["condition"] = it; pills.add("condition" to if (it == "neu") "Neu" else if (it == "defekt") "Defekt" else "Gebraucht") }
                    r.fuelType?.let { newFilters["fuelType"] = it; pills.add("fuelType" to it) }
                    r.transmission?.let { newFilters["transmission"] = it; pills.add("transmission" to it) }
                    r.year?.let { newFilters["yearMin"] = it.toString(); pills.add("yearMin" to "ab $it") }
                    r.mileageMax?.let { newFilters["mileageMax"] = it.toString(); pills.add("mileageMax" to "bis ${it}km") }
                    r.powerMin?.let { newFilters["powerMin"] = it.toString(); pills.add("powerMin" to "ab ${it}PS") }
                    r.propertyType?.let { newFilters["propertyType"] = it; pills.add("propertyType" to if (it == "rent") "Miete" else "Kauf") }
                    r.rooms?.let { newFilters["roomsMin"] = it.formatMeasure(); pills.add("roomsMin" to "${it.formatMeasure()} Zi") }
                    r.sqmMin?.let { newFilters["sqmMin"] = it.formatMeasure(); pills.add("sqmMin" to "ab ${it.formatMeasure()}m²") }
                    r.jobType?.let { newFilters["jobType"] = it; pills.add("jobType" to it) }
                    r.material?.let { newFilters["material"] = it; pills.add("material" to it) }
                    r.gender?.let { newFilters["gender"] = it; pills.add("gender" to it) }
                    r.color?.let { newFilters["color"] = it; pills.add("color" to it) }
                    r.sort?.let { _selectedSort.value = it }

                    _filters.value = newFilters
                    _nlpPills.value = pills
                    // Keep what the user typed. Writing q_clean back into the field
                    // rewrites (and sometimes empties) the text mid-typing and retriggers
                    // this very effect; the cleaned term is only used for the query below.
                    cleanedQuery = r.q_clean
                }
            } catch (_: Exception) {}
            search()
        }
    }

    /** Loads page 1 and replaces the result list. */
    fun search() {
        // A running append must not overwrite the fresh page-1 result.
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        _isLoading.value = true
        runSearch(page = 1)
    }

    /**
     * Appends the next page. Ignored while a request is running or once the last page
     * has been reached.
     *
     * The flag is raised here, not inside the coroutine — a scroll calls this on every
     * frame, and an asynchronous guard would let dozens of requests through before the
     * first one flipped it.
     */
    fun loadMore() {
        if (_isLoading.value || _isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        loadMoreJob = runSearch(page = _page.value + 1)
    }

    private var loadMoreJob: kotlinx.coroutines.Job? = null

    private fun runSearch(page: Int) = viewModelScope.launch {
            try {
                val f = _filters.value
                val response = apiClient.api.search(
                    query = (cleanedQuery ?: _query.value).ifBlank { null },
                    category = _selectedCategory.value,
                    condition = f["condition"], minPrice = f["minPrice"], maxPrice = f["maxPrice"],
                    location = f["location"], radius = f["radius"],
                    shipping = f["shipping"], hasImages = f["hasImages"],
                    sort = _selectedSort.value,
                    brand = f["brand"], model = f["model"],
                    yearMin = f["yearMin"], yearMax = f["yearMax"],
                    mileageMax = f["mileageMax"], fuelType = f["fuelType"],
                    transmission = f["transmission"], powerMin = f["powerMin"], powerMax = f["powerMax"],
                    color = f["color"], accidentFree = f["accidentFree"],
                    propertyType = f["propertyType"], roomsMin = f["roomsMin"], roomsMax = f["roomsMax"],
                    sqmMin = f["sqmMin"], sqmMax = f["sqmMax"],
                    furnished = f["furnished"], balcony = f["balcony"], elevator = f["elevator"],
                    parking = f["parking"], garden = f["garden"], cellar = f["cellar"],
                    jobType = f["jobType"], jobBranche = f["jobBranche"],
                    salaryMin = f["salaryMin"], salaryMax = f["salaryMax"],
                    experienceLevel = f["experienceLevel"], homeOffice = f["homeOffice"],
                    priceUnit = f["priceUnit"], serviceArea = f["serviceArea"],
                    experience = f["experience"], availability = f["availability"],
                    ram = f["ram"], storage = f["storage"],
                    gender = f["gender"], clothingSize = f["clothingSize"],
                    shoeSize = f["shoeSize"], material = f["material"],
                    sportType = f["sportType"], frameSize = f["frameSize"],
                    animalType = f["animalType"], breed = f["breed"],
                    animalAge = f["animalAge"], animalGender = f["animalGender"],
                    vaccinated = f["vaccinated"], neutered = f["neutered"],
                    ageGroup = f["ageGroup"], instrumentType = f["instrumentType"],
                    collectType = f["collectType"], rarity = f["rarity"], era = f["era"],
                    widthMax = f["widthMax"], heightMax = f["heightMax"],
                    gartenType = f["gartenType"],
                    page = page,
                    limit = PAGE_SIZE,
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val fresh = body?.listings ?: emptyList()
                    // Guard against duplicates: a listing bumped between two page loads
                    // shifts the server-side window and can repeat an entry.
                    _results.value = if (page == 1) fresh else {
                        val seen = _results.value.mapTo(HashSet()) { it.id }
                        _results.value + fresh.filter { seen.add(it.id) }
                    }
                    _totalResults.value = body?.total ?: 0
                    _page.value = page
                    _hasMore.value = page < (body?.totalPages ?: 1) && fresh.isNotEmpty()
                    _error.value = null
                } else if (page == 1) {
                    _error.value = "Suche fehlgeschlagen (${response.code()})"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (page == 1) _error.value = "Keine Verbindung zum Server"
            }
        _isLoading.value = false
        _isLoadingMore.value = false
    }

    fun createSavedSearch(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val f = _filters.value
                val req = CreateSavedSearchRequest(
                    name = name,
                    query = _query.value.ifBlank { null },
                    category = _selectedCategory.value,
                    subcategory = f["subcategory"],
                    minPrice = f["minPrice"]?.toDoubleOrNull(),
                    maxPrice = f["maxPrice"]?.toDoubleOrNull(),
                    condition = f["condition"],
                    location = f["location"],
                    radius = f["radius"]?.toIntOrNull(),
                    shipping = f["shipping"]?.toBooleanStrictOrNull()
                )
                val resp = apiClient.api.createSavedSearch(req)
                onResult(resp.isSuccessful)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}

/** Results fetched per page while scrolling. The server caps this at 50. */
private const val PAGE_SIZE = 30

/** How many items before the end the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 6

/**
 * Watches a lazy list and asks for the next page shortly before the end is reached.
 * snapshotFlow keeps this off the composition — it only reacts to actual scrolls.
 */
@Composable
private fun InfiniteScroll(
    lastVisible: () -> Int?,
    total: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(total) {
        snapshotFlow { lastVisible() }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null && total > 0 && index >= total - LOAD_MORE_THRESHOLD) onLoadMore()
            }
    }
}

/** Spinner shown at the end of the list while the next page is on its way. */
@Composable
private fun LoadMoreFooter() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Color(0xFF00BC7D))
    }
}

val CATEGORIES = listOf(
    "all" to "🔥 Alle", "Flohmarkt" to "🛍️ Flohmarkt", "Autos" to "🚗 Autos",
    "Immobilien" to "🏠 Immobilien", "Jobs" to "💼 Jobs", "Elektronik" to "💻 Elektronik",
    "Mode" to "👗 Mode", "Möbel" to "🛋️ Möbel", "Sport" to "🚴 Sport",
    "Garten" to "🌿 Garten", "Dienstleistungen" to "🔧 Services",
    "Haustiere" to "🐕 Haustiere", "Baby & Kind" to "👶 Kids",
    "Musik" to "🎵 Musik", "Sammeln" to "🏆 Sammeln",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onListingClick: (String) -> Unit,
    onBack: () -> Unit,
    searchViewModel: SearchViewModel = viewModel()
) {
    val results by searchViewModel.results.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val query by searchViewModel.query.collectAsState()
    val totalResults by searchViewModel.totalResults.collectAsState()
    val hasMore by searchViewModel.hasMore.collectAsState()
    val selectedCategory by searchViewModel.selectedCategory.collectAsState()
    val selectedSort by searchViewModel.selectedSort.collectAsState()
    val filters by searchViewModel.filters.collectAsState()
    val nlpPills by searchViewModel.nlpPills.collectAsState()

    val previewItems by searchViewModel.preview.collectAsState()
    val showPreview by searchViewModel.showPreview.collectAsState()
    val locationSuggestions by searchViewModel.locationSuggestions.collectAsState()
    val mapPins by searchViewModel.mapPins.collectAsState()
    val mapLoading by searchViewModel.mapLoading.collectAsState()
    var selectedPin by remember { mutableStateOf<MapPin?>(null) }

    // Shared saved-listings state so each card can show its own heart.
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as NimmdasApp
    val savedIds by app.watchlist.savedIds.collectAsState()
    LaunchedEffect(Unit) { app.watchlist.ensureLoaded() }
    var luckyEmpty by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewMode by remember { mutableStateOf("list") } // "grid" or "list"
    val catFilters = selectedCategory?.let { CATEGORY_FILTERS[it] } ?: emptyList()
    val activeCount = filters.size + (if (selectedCategory != null) 1 else 0)

    // Re-entering the tab with a category chip tapped on the home screen: the ViewModel
    // may be reused, in which case its init block never runs again.
    LaunchedEffect(Unit) {
        PendingSearch.consumeCategory()?.let { searchViewModel.setCategory(it) }
    }

    // Typewriter effect state
    val placeholders = listOf(
        "Wonach suchst du?", "iPhone 15 Pro Max...", "Günstige BMW 3er...",
        "Traumwohnung in Wien...", "Vintage Möbel...", "Jobs in Österreich..."
    )
    var currentIdx by remember { mutableIntStateOf(0) }
    var placeholderText by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    var loc by remember { mutableStateOf("de") }
    LaunchedEffect(Unit) {
        loc = (context.applicationContext as NimmdasApp).apiClient.getLocale()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSavedSearchDialog by remember { mutableStateOf(false) }
    var savedSearchName by remember { mutableStateOf("") }

    LaunchedEffect(placeholderText, isDeleting, currentIdx) {
        val target = placeholders[currentIdx % placeholders.size]
        if (isDeleting) {
            if (placeholderText.isEmpty()) {
                isDeleting = false
                currentIdx++
                kotlinx.coroutines.delay(500)
            } else {
                placeholderText = placeholderText.dropLast(1)
                kotlinx.coroutines.delay(40)
            }
        } else {
            if (placeholderText == target) {
                kotlinx.coroutines.delay(2500)
                isDeleting = true
            } else {
                placeholderText = target.take(placeholderText.length + 1)
                kotlinx.coroutines.delay(80)
            }
        }
    }

    // Debounced Live Search
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            kotlinx.coroutines.delay(400) // 400ms debounce
            searchViewModel.searchWithNlp()
        } else if (query.trim().isEmpty()) {
            // Optional: clear results when empty
        }
    }

    // Instant preview dropdown — shorter debounce than the full search so it feels live
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(200)
        searchViewModel.loadPreview(query)
    }

    if (luckyEmpty) {
        LaunchedEffect(Unit) {
            android.widget.Toast.makeText(
                context, "Nichts gefunden – probier andere Filter", android.widget.Toast.LENGTH_SHORT
            ).show()
            luckyEmpty = false
        }
    }

    // Dialog for creating a saved search
    if (showSavedSearchDialog) {
        val defaultName = remember {
            val q = query.trim()
            val cat = selectedCategory ?: ""
            when {
                q.isNotBlank() && cat.isNotBlank() -> "$q in $cat"
                q.isNotBlank() -> q
                cat.isNotBlank() -> cat
                else -> "Meine Suche"
            }
        }
        LaunchedEffect(Unit) {
            savedSearchName = defaultName
        }

        AlertDialog(
            onDismissRequest = { showSavedSearchDialog = false },
            title = { Text(AppTranslations.t("save_search_title", loc), fontWeight = FontWeight.Bold) },
            shape = RoundedCornerShape(24.dp),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(AppTranslations.t("save_search_desc", loc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    OutlinedTextField(
                        value = savedSearchName,
                        onValueChange = { savedSearchName = it },
                        label = { Text(AppTranslations.t("save_search_placeholder", loc)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = savedSearchName.ifBlank { defaultName }
                        searchViewModel.createSavedSearch(name) { success ->
                            scope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(AppTranslations.t("save_search_success", loc))
                                } else {
                                    snackbarHostState.showSnackbar(AppTranslations.t("save_search_error", loc))
                                }
                            }
                        }
                        showSavedSearchDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BC7D))
                ) {
                    Text("Abonnieren", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavedSearchDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    val isDark = isSystemInDarkTheme()
    val ambientGreenGlow = if (isDark) Color(0x0C00BC7D) else Color(0x0600BC7D)
    val ambientBlueGlow = if (isDark) Color(0x0C009688) else Color(0x05009688)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .drawBehind {
                    // Draw a soft ambient glowing circle in the top right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientGreenGlow, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.1f),
                            radius = size.width * 0.7f
                        ),
                        radius = size.width * 0.7f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.1f)
                    )
                    // Draw a soft ambient glowing circle in the bottom left
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientBlueGlow, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.7f),
                            radius = size.width * 0.8f
                        ),
                        radius = size.width * 0.8f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.7f)
                    )
                }
        ) {
            // ── Search bar
            TopAppBar(
                title = {
                    // Pill-shaped field on a soft surface: one clear tap target, no
                    // competing outlines, and the clear button only when it's useful.
                    TextField(
                        value = query, onValueChange = { searchViewModel.setQuery(it) },
                        placeholder = {
                            Text(
                                if (placeholderText.isEmpty()) " " else placeholderText,
                                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF00BC7D),
                        ),
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = {
                                    searchViewModel.setQuery(""); searchViewModel.dismissPreview(); searchViewModel.search()
                                }) { Icon(Icons.Filled.Close, "Leeren", Modifier.size(18.dp)) }
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFF00BC7D), modifier = Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchViewModel.searchWithNlp() })
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = {
                        searchViewModel.lucky(
                            onFound = { id -> searchViewModel.dismissPreview(); onListingClick(id) },
                            onNone = { luckyEmpty = true },
                        )
                    }) {
                        Icon(Icons.Filled.Casino, "Auf gut Glück", tint = Color(0xFF00BC7D))
                    }
                    IconButton(onClick = { showSavedSearchDialog = true }) {
                        Icon(Icons.Filled.Notifications, "Suchagent erstellen", tint = Color(0xFF00BC7D))
                    }
                    BadgedBox(badge = { if (activeCount > 0) Badge { Text("$activeCount") } }) {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Filled.Tune, "Filter", tint = if (showFilters) Color(0xFF00BC7D) else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            )

            // ── Live preview while typing
            if (showPreview && previewItems.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        previewItems.take(5).forEach { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        searchViewModel.dismissPreview()
                                        onListingClick(item.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(
                                    model = item.image?.let {
                                        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    item.location?.let {
                                        Text(it, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f), maxLines = 1)
                                    }
                                }
                                val p = item.price
                                if (p != null && p > 0) {
                                    Text("€ ${p.toLong()}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00BC7D))
                                }
                            }
                        }
                        Text(
                            "Alle Ergebnisse anzeigen",
                            Modifier.fillMaxWidth()
                                .clickable { searchViewModel.dismissPreview(); searchViewModel.searchWithNlp() }
                                .padding(vertical = 10.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BC7D), textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── NLP pills
            if (nlpPills.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(nlpPills) { (key, label) ->
                        InputChip(selected = true, onClick = { searchViewModel.removeFilter(key) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(50))
                    }
                    item {
                        TextButton(onClick = { searchViewModel.clearAllFilters() }) {
                            Text("Zurücksetzen", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Category tabs
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CATEGORIES) { (id, label) ->
                    FilterChip(
                        selected = if (id == "all") selectedCategory == null else selectedCategory == id,
                        onClick = { searchViewModel.setCategory(if (id == "all") null else id) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                        shape = RoundedCornerShape(12.dp) // Large rounded category bento tabs!
                    )
                }
            }

            // ── Filter sheet — slides up from the bottom, like the website's filter panel
            if (showFilters) {
                ModalBottomSheet(
                    onDismissRequest = { showFilters = false },
                    sheetState = filterSheetState,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ) {
                    LazyColumn(
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).heightIn(max = 620.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Tune, null, Modifier.size(20.dp), tint = Color(0xFF00BC7D))
                                    Text("Filter & Sortierung", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { showFilters = false }) {
                                    Icon(Icons.Filled.Close, "Schließen")
                                }
                            }
                        }
                        // ── Category-specific filters
                        if (catFilters.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.width(3.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF00BC7D)))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${selectedCategory}-Filter", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
                            }
                            items(catFilters) { cf -> FilterField(cf, filters[cf.key] ?: "") { searchViewModel.setFilter(cf.key, it) } }
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        }

                        // ── Universal: Price
                        item { Text("Preis", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = filters["minPrice"] ?: "", onValueChange = { searchViewModel.setFilter("minPrice", it) },
                                    label = { Text("Min €") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = filters["maxPrice"] ?: "", onValueChange = { searchViewModel.setFilter("maxPrice", it) },
                                    label = { Text("Max €") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
                            }
                        }

                        // ── Location (with autocomplete suggestions)
                        item {
                            Column {
                                OutlinedTextField(
                                    value = filters["location"] ?: "",
                                    onValueChange = {
                                        searchViewModel.setFilter("location", it)
                                        searchViewModel.loadLocationSuggestions(it)
                                    },
                                    label = { Text("Ort / PLZ") },
                                    leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp)) },
                                    singleLine = true, shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (locationSuggestions.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    locationSuggestions.take(5).forEach { s ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clickable {
                                                    searchViewModel.setFilter("location", s.location)
                                                    searchViewModel.clearLocationSuggestions()
                                                    searchViewModel.search()
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(s.display(), fontSize = 12.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            if (s.count > 0) {
                                                Text("${s.count}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Condition (hide for Jobs/Immobilien/Dienstleistungen)
                        if (selectedCategory !in HIDE_CONDITION_CATEGORIES) {
                            item { Text("Zustand", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(null to "Alle", "neu" to "Neu", "gebraucht" to "Gebraucht", "defekt" to "Defekt").forEach { (v, l) ->
                                        FilterChip(selected = filters["condition"] == v, onClick = { searchViewModel.setFilter("condition", v ?: "") },
                                            label = { Text(l, fontSize = 12.sp) }, shape = RoundedCornerShape(50))
                                    }
                                }
                            }
                        }

                        // ── Sort
                        item { Text("Sortieren", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("newest" to "Neueste", "price_asc" to "€ ↑", "price_desc" to "€ ↓", "popular" to "Beliebt").forEach { (v, l) ->
                                    FilterChip(selected = selectedSort == v, onClick = { searchViewModel.setSort(v) },
                                        label = { Text(l, fontSize = 12.sp) }, shape = RoundedCornerShape(50))
                                }
                            }
                        }

                        // ── Extras (hide for non-physical categories)
                        if (selectedCategory !in HIDE_CONDITION_CATEGORIES) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = filters["shipping"] == "true",
                                        onClick = { searchViewModel.setFilter("shipping", if (filters["shipping"] == "true") "" else "true") },
                                        label = { Text("📦 Versand", fontSize = 12.sp) }, shape = RoundedCornerShape(50))
                                    FilterChip(selected = filters["hasImages"] == "true",
                                        onClick = { searchViewModel.setFilter("hasImages", if (filters["hasImages"] == "true") "" else "true") },
                                        label = { Text("📷 Fotos", fontSize = 12.sp) }, shape = RoundedCornerShape(50))
                                }
                            }
                        }

                        // ── Apply
                        item {
                            Button(
                                onClick = { searchViewModel.search(); showFilters = false }, 
                                Modifier.fillMaxWidth(), 
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BC7D))
                            ) {
                                Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(if (activeCount > 0) "$activeCount Filter anwenden" else "Suchen", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ── Results count + view toggle
            // Always shown in map mode: without it there is no way back to the list.
            if (totalResults > 0 || results.isNotEmpty() || viewMode == "map") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (totalResults > 0) "$totalResults Ergebnisse" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                    // View toggle: Grid / List
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            onClick = { viewMode = "grid" },
                            shape = RoundedCornerShape(50),
                            color = if (viewMode == "grid") MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (viewMode == "grid") 1.dp else 0.dp
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.GridView, null, Modifier.size(14.dp), tint = if (viewMode == "grid") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text("Kacheln", fontSize = 11.sp, fontWeight = if (viewMode == "grid") FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (viewMode == "grid") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                        Surface(
                            onClick = { viewMode = "list" },
                            shape = RoundedCornerShape(50),
                            color = if (viewMode == "list") MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (viewMode == "list") 1.dp else 0.dp
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ViewList, null, Modifier.size(14.dp), tint = if (viewMode == "list") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text("Liste", fontSize = 11.sp, fontWeight = if (viewMode == "list") FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (viewMode == "list") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                        Surface(
                            onClick = { viewMode = "map"; searchViewModel.loadMapPins() },
                            shape = RoundedCornerShape(50),
                            color = if (viewMode == "map") MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (viewMode == "map") 1.dp else 0.dp
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Map, null, Modifier.size(14.dp), tint = if (viewMode == "map") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                Text("Karte", fontSize = 11.sp, fontWeight = if (viewMode == "map") FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (viewMode == "map") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(0.4f))
                            }
                        }
                    }
                }
            }

            // ── Map view
            if (viewMode == "map") {
                // weight, not fillMaxSize: inside a Column the latter claims the whole
                // screen height and pushes the view toggle out of reach.
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    ClusterMap(pins = mapPins, onPinClick = { pin -> selectedPin = pin })
                    if (mapLoading) {
                        Surface(
                            Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 3.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                                    color = Color(0xFF00BC7D))
                                Text("Karte wird geladen…", fontSize = 12.sp)
                            }
                        }
                    } else if (mapPins.isNotEmpty()) {
                        Surface(
                            Modifier.align(Alignment.TopCenter).padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(0.94f),
                            shadowElevation = 3.dp,
                        ) {
                            Text("${mapPins.size} auf der Karte",
                                Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Preview card for the tapped pin
                    selectedPin?.let { pin ->
                        Surface(
                            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth()
                                .clickable { onListingClick(pin.id) },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp,
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AsyncImage(
                                    model = pin.image?.let {
                                        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(pin.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    pin.location?.let {
                                        Text(it, fontSize = 11.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    }
                                    val p = pin.price
                                    if (p != null && p > 0) {
                                        Text("€ ${p.toLong()}", fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold, color = Color(0xFF00BC7D))
                                    }
                                }
                                IconButton(onClick = { selectedPin = null }) {
                                    Icon(Icons.Filled.Close, "Schließen", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            } else {

            // ── Results
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF00BC7D), strokeWidth = 3.dp) }
            } else if (results.isEmpty() && (query.isNotBlank() || selectedCategory != null || filters.isNotEmpty())) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("😔", fontSize = 48.sp)
                                Text("Keine Ergebnisse", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Versuche andere Filter oder erstelle einen Suchagenten, um bei neuen Angeboten direkt benachrichtigt zu werden.", 
                                    fontSize = 13.sp, 
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { showSavedSearchDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BC7D))
                                ) {
                                    Icon(Icons.Filled.Notifications, null, modifier = Modifier.size(18.dp), tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(AppTranslations.t("save_search_btn", loc), fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else if (viewMode == "grid") {
                // Two tiles per row, like the website's mobile grid.
                val gridState = rememberLazyGridState()
                InfiniteScroll(
                    lastVisible = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
                    total = results.size,
                    onLoadMore = { searchViewModel.loadMore() },
                )
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(results, key = { it.id }) { listing ->
                        ListingCard(
                            listing,
                            onClick = { onListingClick(listing.id) },
                            modifier = Modifier.fillMaxWidth(),
                            viewMode = "grid",
                            isSaved = listing.id in savedIds,
                            onToggleSave = { app.watchlist.toggle(listing.id) },
                        )
                    }
                    if (hasMore) {
                        item(span = { GridItemSpan(2) }) { LoadMoreFooter() }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                InfiniteScroll(
                    lastVisible = { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
                    total = results.size,
                    onLoadMore = { searchViewModel.loadMore() },
                )
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(results, key = { it.id }) { listing ->
                        ListingCard(
                            listing, onClick = { onListingClick(listing.id) },
                            modifier = Modifier.fillMaxWidth(), viewMode = viewMode,
                            isSaved = listing.id in savedIds,
                            onToggleSave = { app.watchlist.toggle(listing.id) },
                        )
                    }
                    if (hasMore) {
                        item { LoadMoreFooter() }
                    }
                }
            }
            } // end of non-map branch
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterField(cf: CatFilter, value: String, onValueChange: (String) -> Unit) {
    when (cf.type) {
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(value = if (cf.key == "propertyType") (if (value == "rent") "Miete" else if (value == "buy") "Kauf" else value) else value,
                    onValueChange = {}, readOnly = true, label = { Text(cf.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp))
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Alle") }, onClick = { onValueChange(""); expanded = false })
                    cf.options?.forEach { opt ->
                        val display = if (cf.key == "propertyType") (if (opt == "rent") "Miete" else "Kauf") else opt
                        DropdownMenuItem(text = { Text(display) }, onClick = { onValueChange(opt); expanded = false })
                    }
                }
            }
        }
        "boolean" -> {
            FilterChip(selected = value == "true", onClick = { onValueChange(if (value == "true") "" else "true") },
                label = { Text(cf.label, fontSize = 12.sp) }, shape = RoundedCornerShape(50))
        }
        else -> {
            OutlinedTextField(value = value, onValueChange = onValueChange,
                label = { Text(cf.label + (cf.unit?.let { " ($it)" } ?: "")) },
                placeholder = cf.placeholder?.let { { Text(it) } },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = if (cf.placeholder?.all { it.isDigit() } == true) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default)
        }
    }
}
