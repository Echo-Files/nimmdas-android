package at.nimmdas.app.ui.screens.create

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.Suggestions
import at.nimmdas.app.data.model.CreateListingRequest
import at.nimmdas.app.data.model.Listing
import at.nimmdas.app.data.model.ImageMarker
import at.nimmdas.app.data.model.formatMeasure
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectTapGestures
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import androidx.compose.ui.input.pointer.pointerInput
data class CategoryInfo(val name: String, val emoji: String, val desc: String, val color: Color)

val CATEGORIES = listOf(
    CategoryInfo("Flohmarkt", "🛍️", "Gebrauchtes, Vintage", Color(0xFFFF9800)),
    CategoryInfo("Autos", "🚗", "PKW, Motorrad, Zubehör", Color(0xFF2196F3)),
    CategoryInfo("Immobilien", "🏠", "Wohnungen, Häuser", Color(0xFF4CAF50)),
    CategoryInfo("Jobs", "💼", "Stellenangebote", Color(0xFF3F51B5)),
    CategoryInfo("Elektronik", "💻", "Smartphones, Laptops", Color(0xFF9C27B0)),
    CategoryInfo("Mode", "👗", "Kleidung, Schuhe", Color(0xFFE91E63)),
    CategoryInfo("Möbel", "🛋️", "Wohnzimmer, Küche", Color(0xFFFFC107)),
    CategoryInfo("Sport", "🚴", "Fitness, Fahrräder", Color(0xFF8BC34A)),
    CategoryInfo("Garten", "🌿", "Pflanzen, Werkzeug", Color(0xFF009688)),
    CategoryInfo("Haustiere", "🐕", "Zubehör, Tierbedarf", Color(0xFFFF5722)),
    CategoryInfo("Baby & Kind", "👶", "Kinderwagen, Spielzeug", Color(0xFF03A9F4)),
    CategoryInfo("Musik", "🎵", "Instrumente, Equipment", Color(0xFFE040FB)),
    CategoryInfo("Sammeln", "🏆", "Münzen, Antiquitäten", Color(0xFFFFD600)),
    CategoryInfo("Dienstleistungen", "🔧", "Handwerk, IT", Color(0xFF607D8B)),
    CategoryInfo("Events & Märkte", "🎪", "Flohmärkte, Messen", Color(0xFFEF6C00)),
)

val SUBCATEGORIES = mapOf(
    "Flohmarkt" to listOf("Hausrat","Bücher","Dekoration","Vintage","Werkzeug","Spielzeug","Sonstiges"),
    "Elektronik" to listOf("Smartphones","Laptops","Konsolen","Audio","Kameras","Tablets","TV","Zubehör"),
    "Mode" to listOf("Herren","Damen","Kinder","Schuhe","Accessoires","Schmuck","Uhren"),
    "Möbel" to listOf("Wohnzimmer","Schlafzimmer","Küche","Bad","Büro","Garten","Beleuchtung"),
    "Autos" to listOf("PKW","SUV","Kombi","Limousine","Cabrio","Transporter","Motorrad","E-Auto","Oldtimer","Ersatzteile"),
    "Immobilien" to listOf("Wohnung","Haus","Zimmer","Büro","Grundstück","Garage","Gewerbe"),
    "Sport" to listOf("Fitness","Fahrräder","Wintersport","Ballsport","Outdoor","Camping","E-Bikes"),
    "Garten" to listOf("Pflanzen","Werkzeug","Möbel","Pool","Rasenmäher","Griller"),
    "Jobs" to listOf("Vollzeit","Teilzeit","Minijob","Praktikum","Freelancer","Remote"),
    "Haustiere" to listOf("Hunde","Katzen","Kleintiere","Vögel","Aquaristik","Zubehör"),
    "Baby & Kind" to listOf("Kinderwagen","Spielzeug","Kleidung","Möbel","Schule"),
    "Musik" to listOf("Gitarren","Keyboards","Schlagzeug","DJ-Equipment","Vinyl"),
    "Sammeln" to listOf("Münzen","Briefmarken","Antiquitäten","Trading Cards","Figuren"),
    "Dienstleistungen" to listOf("Handwerk","Transport","Nachhilfe","IT & Web","Reinigung"),
    "Events & Märkte" to listOf("Flohmarkt Termin","Garagenflohmarkt","Kleiderbasar","Messe","Ausstellung","Sonstiges"),
)

val SUBCATEGORY_ICONS = mapOf(
    "Hausrat" to "🏠", "Bücher" to "📚", "Dekoration" to "🖼️", "Vintage" to "🕰️", "Werkzeug" to "🔧", "Spielzeug" to "🧸", "Sonstiges" to "📌",
    "Smartphones" to "📱", "Laptops" to "💻", "Konsolen" to "🎮", "Audio" to "🎧", "Kameras" to "📷", "Tablets" to "📋", "TV" to "📺", "Zubehör" to "🔌",
    "Herren" to "👔", "Damen" to "👗", "Kinder" to "🧒", "Schuhe" to "👟", "Accessoires" to "⌚", "Schmuck" to "💎", "Uhren" to "⏱️",
    "Wohnzimmer" to "🛋️", "Schlafzimmer" to "🛏️", "Küche" to "🍳", "Bad" to "🚿", "Büro" to "🖥️", "Garten" to "🌿", "Beleuchtung" to "💡",
    "PKW" to "🚗", "SUV" to "🚙", "Kombi" to "🚐", "Limousine" to "🏎️", "Cabrio" to "🏖️", "Transporter" to "🚚", "Motorrad" to "🏍️", "E-Auto" to "⚡", "Oldtimer" to "🏛️", "Ersatzteile" to "⚙️",
    "Wohnung" to "🏢", "Haus" to "🏡", "Zimmer" to "🚪", "Grundstück" to "🌳", "Garage" to "🅿️", "Gewerbe" to "🏪",
    "Fitness" to "💪", "Fahrräder" to "🚲", "Wintersport" to "⛷️", "Ballsport" to "⚽", "Outdoor" to "🏕️", "Camping" to "⛺", "E-Bikes" to "🔋",
    "Pflanzen" to "🌱", "Möbel" to "🪑", "Pool" to "🏊", "Rasenmäher" to "🌿", "Griller" to "🔥",
    "Vollzeit" to "⏰", "Teilzeit" to "🕐", "Minijob" to "💰", "Praktikum" to "🎓", "Freelancer" to "🧑‍💻", "Remote" to "🏠",
    "Hunde" to "🐕", "Katzen" to "🐈", "Kleintiere" to "🐹", "Vögel" to "🦜", "Aquaristik" to "🐠",
    "Kinderwagen" to "👶", "Kleidung" to "👕", "Schule" to "🎒",
    "Gitarren" to "🎸", "Keyboards" to "🎹", "Schlagzeug" to "🥁", "DJ-Equipment" to "🎛️", "Vinyl" to "💿",
    "Münzen" to "🪙", "Briefmarken" to "📬", "Antiquitäten" to "🏺", "Trading Cards" to "🃏", "Figuren" to "🧩",
    "Handwerk" to "🔨", "Transport" to "📦", "Nachhilfe" to "📝", "IT & Web" to "💻", "Reinigung" to "🧹"
)

// ═══ ViewModel ═══
class CreateListingViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _success = MutableStateFlow(false)
    val success = _success.asStateFlow()

    private val _editListing = MutableStateFlow<Listing?>(null)
    val editListing = _editListing.asStateFlow()

    /** Guesses the category from a title; returns null when nothing matched. */
    suspend fun predictCategory(title: String): at.nimmdas.app.data.model.CategoryPrediction? = try {
        val res = apiClient.api.predictCategory(title)
        res.body()?.takeIf { it.category.isNotBlank() }
    } catch (_: Exception) { null }

    fun loadListingForEdit(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = apiClient.api.getListingById(id)
                if (res.isSuccessful) _editListing.value = res.body()
            } catch (e: Exception) {
                _error.value = "Konnte Inserat nicht laden"
            }
            _isLoading.value = false
        }
    }

    fun submitListing(context: Context, req: CreateListingRequest, images: List<Uri>, editId: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                // 1. Upload Images if any
                var uploadedUrls = emptyList<String>()
                if (images.isNotEmpty()) {
                    val parts = images.mapNotNull { uri ->
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val requestBody = okhttp3.RequestBody.create("image/jpeg".toMediaTypeOrNull(), bytes)
                            okhttp3.MultipartBody.Part.createFormData("images", "image_${System.currentTimeMillis()}.jpg", requestBody)
                        }
                    }
                    if (parts.isNotEmpty()) {
                        val uploadRes = apiClient.api.uploadImages(parts)
                        if (uploadRes.isSuccessful) {
                            uploadedUrls = uploadRes.body()?.urls ?: emptyList()
                        } else {
                            _error.value = "Bilder-Upload fehlgeschlagen (${uploadRes.code()})"
                            _isLoading.value = false
                            return@launch
                        }
                    }
                }

                // 2. Combine with existing images and markers
                val existingImages = if (editId != null) _editListing.value?.images ?: emptyList() else emptyList()
                val finalImages = existingImages + uploadedUrls
                
                // Wir passen die ImageIndexe der Marker an (falls nötig, gehen wir davon aus, dass sie sich auf die neue "finalImages" Liste beziehen).
                // Die Marker in `req` sind schon auf die finale Liste abgestimmt.
                
                val finalReq = req.copy(images = finalImages.ifEmpty { null })

                // 3. Save Listing
                val response = if (editId != null) {
                    apiClient.api.updateListing(editId, finalReq)
                } else {
                    apiClient.api.createListing(finalReq)
                }
                
                if (response.isSuccessful) { _success.value = true; onSuccess() }
                else _error.value = "Fehler beim Speichern (${response.code()})"
            } catch (e: Exception) { _error.value = "Netzwerkfehler: ${e.localizedMessage}" }
            _isLoading.value = false
        }
    }
}

// ═══ Screen ═══
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    editListingId: String? = null,
    onBack: () -> Unit, onCreated: () -> Unit,
    viewModel: CreateListingViewModel = viewModel()
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }
    
    // States
    var category by remember { mutableStateOf("") }
    var subcategory by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var priceType by remember { mutableStateOf("fest") }
    var condition by remember { mutableStateOf("gebraucht") }
    var location by remember { mutableStateOf("") }
    var shipping by remember { mutableStateOf(false) }
    // Auto
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("") }
    var transmission by remember { mutableStateOf("") }
    var power by remember { mutableStateOf("") }
    var accidentFree by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf("") }
    var modelVariant by remember { mutableStateOf("") }
    var registrationDate by remember { mutableStateOf("") }
    var owners by remember { mutableStateOf("") }
    var tuev by remember { mutableStateOf("") }
    // Immobilien
    var sqm by remember { mutableStateOf("") }
    var rooms by remember { mutableStateOf("") }
    var propertyType by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var totalFloors by remember { mutableStateOf("") }
    var heatingType by remember { mutableStateOf("") }
    var energyClass by remember { mutableStateOf("") }
    var availableFrom by remember { mutableStateOf("") }
    var furnished by remember { mutableStateOf(false) }
    var balcony by remember { mutableStateOf(false) }
    var elevator by remember { mutableStateOf(false) }
    var parking by remember { mutableStateOf(false) }
    var cellar by remember { mutableStateOf(false) }
    var garden by remember { mutableStateOf(false) }
    // Jobs
    var companyName by remember { mutableStateOf("") }
    var jobType by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var homeOffice by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf("") }
    var requirements by remember { mutableStateOf("") }
    var benefits by remember { mutableStateOf("") }
    // Dienstleistungen
    var serviceArea by remember { mutableStateOf("") }
    var availability by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var priceUnit by remember { mutableStateOf("") }
    // Events & Märkte
    var eventDate by remember { mutableStateOf("") }
    var eventTime by remember { mutableStateOf("") }
    var eventFrequency by remember { mutableStateOf("Einmalig") }
    var eventAddress by remember { mutableStateOf("") }
    // Elektronik & Co.
    var ram by remember { mutableStateOf("") }
    var storage by remember { mutableStateOf("") }
    var warranty by remember { mutableStateOf("") }
    // Münzrabatt
    var coinDiscountPercent by remember { mutableStateOf("") }
    var coinDiscountMax by remember { mutableStateOf("") }

    // Images
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var imageMarkers by remember { mutableStateOf<List<ImageMarker>>(emptyList()) }
    var selectedImageForMarker by remember { mutableStateOf<Pair<Int, Uri>?>(null) }
    
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        selectedImages = selectedImages + uris
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            selectedImages = selectedImages + cameraUri!!
        }
    }

    fun createImageUri(ctx: Context): Uri? {
        return try {
            val imagesDir = File(ctx.cacheDir, "images")
            imagesDir.mkdirs()
            val file = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Kamera-Ordner Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val editListing by viewModel.editListing.collectAsState()

    LaunchedEffect(editListingId) {
        if (editListingId != null) viewModel.loadListingForEdit(editListingId)
    }

    LaunchedEffect(editListing) {
        editListing?.let {
            category = it.category ?: ""
            subcategory = it.subcategory ?: ""
            title = it.title
            description = it.description ?: ""
            price = it.price?.toString() ?: ""
            priceType = it.priceType ?: "fest"
            condition = it.condition ?: "gebraucht"
            location = it.location ?: ""
            brand = it.brand ?: ""
            model = it.model ?: ""
            year = it.year?.toString() ?: ""
            mileage = it.mileage?.toString() ?: ""
            fuelType = it.fuelType ?: ""
            transmission = it.transmission ?: ""
            power = it.power?.toString() ?: ""
            accidentFree = it.accidentFree ?: false
            sqm = it.squareMeters?.formatMeasure() ?: ""
            rooms = it.rooms?.formatMeasure() ?: ""
            propertyType = it.propertyType ?: ""
            companyName = it.companyName ?: ""
            jobType = it.jobType ?: ""
            salary = it.salary ?: ""
            color = it.color ?: ""
            modelVariant = it.modelVariant ?: ""
            registrationDate = it.registrationDate ?: ""
            owners = it.owners?.toString() ?: ""
            tuev = it.tuev ?: ""
            floor = it.floor?.toString() ?: ""
            totalFloors = it.totalFloors?.toString() ?: ""
            heatingType = it.heatingType ?: ""
            energyClass = it.energyClass ?: ""
            availableFrom = it.availableFrom ?: ""
            furnished = it.furnished ?: false
            balcony = it.balcony ?: false
            elevator = it.elevator ?: false
            parking = it.parking ?: false
            cellar = it.cellar ?: false
            garden = it.garden ?: false
            homeOffice = it.homeOffice ?: false
            startDate = it.startDate ?: ""
            requirements = it.requirements ?: ""
            benefits = it.benefits ?: ""
            serviceArea = it.serviceArea ?: ""
            availability = it.availability ?: ""
            experience = it.experience ?: ""
            priceUnit = it.priceUnit ?: ""
            ram = it.ram ?: ""
            storage = it.storage ?: ""
            warranty = it.warranty ?: ""
            eventDate = it.eventDate ?: ""
            eventTime = it.eventTime ?: ""
            eventFrequency = it.eventFrequency ?: "Einmalig"
            eventAddress = it.eventAddress ?: ""
            // Jump directly to Step 3 if editing
            step = 3
        }
    }

    // Debounced category guess for the quick-create bar on step 1.
    var prediction by remember { mutableStateOf<at.nimmdas.app.data.model.CategoryPrediction?>(null) }
    var isPredicting by remember { mutableStateOf(false) }
    LaunchedEffect(title, step) {
        if (step != 1 || title.trim().length < 2) {
            prediction = null; isPredicting = false
            return@LaunchedEffect
        }
        isPredicting = true
        kotlinx.coroutines.delay(450)
        prediction = viewModel.predictCategory(title.trim())
        isPredicting = false
    }

    val catInfo = CATEGORIES.find { it.name == category }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (step) {
                        1 -> Text("Kategorie wählen", fontWeight = FontWeight.Bold)
                        2 -> Text("Unterkategorie", fontWeight = FontWeight.Bold)
                        3 -> Text("${catInfo?.emoji ?: ""} Details", fontWeight = FontWeight.Bold)
                        4 -> Text("📷 Bilder", fontWeight = FontWeight.Bold)
                        else -> Text("Inserat", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            step > 1 && editListingId == null -> step--
                            step > 3 && editListingId != null -> step--
                            else -> onBack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                }
            )
        }
    ) { padding ->
        if (isLoading && editListingId != null && step < 3) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        AnimatedContent(targetState = step, modifier = Modifier.padding(padding), label = "step") { currentStep ->
            when (currentStep) {
                // ═══ STEP 1: Category Picker ═══
                1 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick-create bar: type the title, the category follows.
                    item(span = { GridItemSpan(2) }) {
                        QuickTitleBar(
                            title = title,
                            onTitleChange = { title = it },
                            prediction = prediction,
                            isPredicting = isPredicting,
                            onAccept = { p ->
                                category = p.category
                                subcategory = p.subcategory
                                if (p.brand.isNotBlank()) brand = p.brand
                                if (p.model.isNotBlank()) model = p.model
                                step = if (p.subcategory.isBlank() && SUBCATEGORIES.containsKey(p.category)) 2 else 3
                            },
                        )
                    }
                    items(CATEGORIES) { cat ->
                        Card(
                            onClick = { category = cat.name; step = if (SUBCATEGORIES.containsKey(cat.name)) 2 else 3 },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                        .background(cat.color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) { Text(cat.emoji, fontSize = 24.sp) }
                                Spacer(Modifier.height(10.dp))
                                Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(cat.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                            }
                        }
                    }
                }

                // ═══ STEP 2: Subcategory Picker ═══
                2 -> {
                    val subs = SUBCATEGORIES[category] ?: emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(subs) { sub ->
                            Card(
                                onClick = { subcategory = sub; step = 3 },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Box(Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    val emoji = SUBCATEGORY_ICONS[sub] ?: "📌"
                                    Text("$emoji $sub", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                        item {
                            TextButton(onClick = { subcategory = ""; step = 3 }, modifier = Modifier.fillMaxWidth()) {
                                Text("Ohne Unterkategorie →", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // ═══ STEP 3: Form Details ═══
                3 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress
                    LinearProgressIndicator(
                        progress = { 0.66f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        color = catInfo?.color ?: MaterialTheme.colorScheme.primary
                    )
                    Text("Schritt 3 von 4 · Details", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))

                    // ── Category-specific fields ──
                    if (category == "Autos") {
                        SectionCard("🚗 Fahrzeugdetails") {
                            AutoCompleteField("Marke *", brand, Suggestions.CAR_BRANDS) { brand = it; model = "" }
                            val modelsForBrand = Suggestions.CAR_MODELS[brand] ?: emptyList()
                            AutoCompleteField("Modell *", model, modelsForBrand) { model = it }
                            
                            AutoCompleteField("Modellvariante", modelVariant, emptyList()) { modelVariant = it }
                            FormField("Baujahr *", year, KeyboardType.Number) { year = it }
                            FormField("Kilometerstand (km) *", mileage, KeyboardType.Number) { mileage = it }
                            FormField("Leistung (PS)", power, KeyboardType.Number) { power = it }
                            DropdownField("Treibstoff *", fuelType, listOf("Benzin","Diesel","Elektro","Hybrid","Gas")) { fuelType = it }
                            DropdownField("Getriebe *", transmission, listOf("Manuell","Automatik")) { transmission = it }
                            AutoCompleteField("Farbe", color, Suggestions.COMMON_COLORS) { color = it }
                            AutoCompleteField("Erstzulassung", registrationDate, Suggestions.REG_DATES) { registrationDate = it }
                            AutoCompleteField("§57a Pickerl gültig bis", tuev, Suggestions.TUEV_DATES) { tuev = it }
                            DropdownField("Vorbesitzer", owners, listOf("1","2","3","4")) { owners = it }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(accidentFree, { accidentFree = it })
                                Text("✅ Unfallfrei", fontSize = 14.sp)
                            }
                        }
                    }
                    if (category == "Immobilien") {
                        SectionCard("🏠 Objektdetails") {
                            DropdownField("Miete / Kauf *", propertyType, listOf("Miete","Kauf")) { propertyType = it }
                            FormField("Wohnfläche (m²) *", sqm, KeyboardType.Number) { sqm = it }
                            FormField("Zimmer *", rooms, KeyboardType.Number) { rooms = it }
                            DropdownField("Stockwerk", floor, Suggestions.FLOOR_OPTIONS) { floor = it }
                            FormField("Stockwerke gesamt", totalFloors, KeyboardType.Number) { totalFloors = it }
                            AutoCompleteField("Heizung", heatingType, Suggestions.HEATING_TYPES) { heatingType = it }
                            DropdownField("Energieeffizienzklasse", energyClass, listOf("A++","A+","A","B","C","D","E","F","G")) { energyClass = it }
                            AutoCompleteField("Verfügbar ab", availableFrom, Suggestions.REALESTATE_AVAIL) { availableFrom = it }
                        }
                        SectionCard("✨ Ausstattung") {
                            listOf(
                                Triple("🛋️ Möbliert", furnished, { v: Boolean -> furnished = v }),
                                Triple("🌇 Balkon / Terrasse", balcony, { v: Boolean -> balcony = v }),
                                Triple("🛗 Lift", elevator, { v: Boolean -> elevator = v }),
                                Triple("🅿️ Parkplatz / Garage", parking, { v: Boolean -> parking = v }),
                                Triple("📦 Keller", cellar, { v: Boolean -> cellar = v }),
                                Triple("🌳 Garten", garden, { v: Boolean -> garden = v }),
                            ).forEach { (label, checked, onChange) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked, onChange)
                                    Text(label, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    if (category == "Jobs") {
                        SectionCard("💼 Stellenangebot") {
                            FormField("Unternehmen *", companyName) { companyName = it }
                            DropdownField("Anstellungsart *", jobType, listOf("Vollzeit","Teilzeit","Minijob","Praktikum","Freelancer")) { jobType = it }
                            AutoCompleteField("Gehalt", salary, Suggestions.SALARIES) { salary = it }
                            AutoCompleteField("Startdatum", startDate, Suggestions.START_DATES) { startDate = it }
                            MultilineField("Anforderungen", requirements) { requirements = it }
                            MultilineField("Wir bieten", benefits) { benefits = it }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(homeOffice, { homeOffice = it })
                                Text("🏠 HomeOffice möglich", fontSize = 14.sp)
                            }
                        }
                    }
                    if (category == "Dienstleistungen") {
                        SectionCard("🔧 Leistungsdetails") {
                            AutoCompleteField("Einsatzgebiet *", serviceArea, Suggestions.SERVICE_AREAS) { serviceArea = it }
                            AutoCompleteField("Verfügbarkeit *", availability, Suggestions.AVAILABILITIES) { availability = it }
                            AutoCompleteField("Erfahrung", experience, Suggestions.EXPERIENCES) { experience = it }
                            DropdownField("Preiseinheit *", priceUnit, listOf("pro Stunde","Pauschalpreis","nach Aufwand","Monatspauschale","Verhandlungsbasis")) { priceUnit = it }
                        }
                    }
                    if (category == "Events & Märkte") {
                        SectionCard("🎪 Termin & Ort") {
                            FormField("Datum (TT.MM.JJJJ) *", eventDate) { eventDate = it }
                            AutoCompleteField("Uhrzeit", eventTime, Suggestions.EVENT_TIMES) { eventTime = it }
                            DropdownField("Häufigkeit", eventFrequency, listOf("Einmalig","Täglich","Wöchentlich","Monatlich","Jährlich")) { eventFrequency = it }
                            AutoCompleteField("Adresse", eventAddress, Suggestions.EVENT_ADDRESSES) { eventAddress = it }
                        }
                    }
                    if (category == "Elektronik") {
                        SectionCard("💾 Technische Daten") {
                            FormField("Arbeitsspeicher (RAM)", ram) { ram = it }
                            FormField("Speicherplatz", storage) { storage = it }
                            AutoCompleteField("Garantie", warranty, Suggestions.WARRANTIES) { warranty = it }
                        }
                    }

                    if (category !in listOf("Autos","Immobilien","Jobs")) {
                        SectionCard("📋 Zustand") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("neu" to "✨ Neu", "gebraucht" to "🔄 Gebraucht", "defekt" to "🔧 Defekt").forEach { (id, label) ->
                                    FilterChip(
                                        selected = condition == id, onClick = { condition = id },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = catInfo?.color?.copy(0.1f) ?: MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = catInfo?.color ?: MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SectionCard("📝 Titel & Preis") {
                        FormField("Titel *", title) { title = it }
                        FormField("Preis (€)", price, KeyboardType.Decimal) { price = it }
                        DropdownField("Preistyp", priceType, listOf("fest" to "Festpreis","vb" to "VB","verschenken" to "Zu verschenken","auf_anfrage" to "Auf Anfrage")) { priceType = it }
                    }

                    // Buyers can pay part of the price with coins — same option as on the web.
                    if (priceType != "verschenken" && priceType != "auf_anfrage") {
                        SectionCard("🪙 Münzrabatt (optional)") {
                            Text(
                                "Käufer können einen Teil des Preises mit Münzen bezahlen. 100 Münzen = € 1.",
                                fontSize = 11.sp, lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            )
                            FormField("Rabatt in % (max. 50)", coinDiscountPercent, KeyboardType.Number) {
                                coinDiscountPercent = it.filter(Char::isDigit).take(2)
                            }
                            FormField("Maximal in €", coinDiscountMax, KeyboardType.Number) {
                                coinDiscountMax = it.filter(Char::isDigit).take(5)
                            }
                        }
                    }

                    SectionCard("📍 Standort") {
                        AutoCompleteField("PLZ / Ort *", location, Suggestions.AUSTRIAN_DISTRICTS) { location = it }
                    }

                    SectionCard("📄 Beschreibung") {
                        OutlinedTextField(
                            value = description, onValueChange = { description = it },
                            label = { Text("Beschreibung *") }, minLines = 4, maxLines = 8,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Shipping
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                            .clickable { shipping = !shipping }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocalShipping, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Versand möglich", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(shipping, { shipping = it })
                    }

                    Button(
                        onClick = { step = 4 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = title.isNotBlank() && location.isNotBlank(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Weiter zu den Fotos", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ═══ STEP 4: Images & Submit ═══
                4 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { 1.0f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        color = catInfo?.color ?: MaterialTheme.colorScheme.primary
                    )
                    Text("Schritt 4 von 4 · Fotos hinzufügen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))

                    SectionCard("📷 Bilder") {
                        if (selectedImages.isNotEmpty() || (editListing?.images?.isNotEmpty() == true)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // show existing images if any
                                if (editListing?.images != null) {
                                    itemsIndexed(editListing!!.images!!) { idx, url ->
                                        val fullUrl = if (url.startsWith("http")) url else "${BuildConfig.API_BASE_URL}$url"
                                        val markers = imageMarkers.filter { it.imageIndex == idx }
                                        Box(modifier = Modifier.size(100.dp).clickable { 
                                            // Optional: open marker dialog for existing images
                                            // Für Einfachheit erlauben wir hier Marker für lokale URIs.
                                        }) {
                                            AsyncImage(model = fullUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                            if (markers.isNotEmpty()) {
                                                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Red, RoundedCornerShape(4.dp)).padding(horizontal=4.dp, vertical=2.dp)) {
                                                    Text("${markers.size} Defekte", color=Color.White, fontSize=10.sp, fontWeight=FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                itemsIndexed(selectedImages) { idx, uri ->
                                    val actualIndex = (editListing?.images?.size ?: 0) + idx
                                    val markers = imageMarkers.filter { it.imageIndex == actualIndex }
                                    Box(modifier = Modifier.size(100.dp).clickable { selectedImageForMarker = actualIndex to uri }) {
                                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                        if (markers.isNotEmpty()) {
                                            Box(Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Red, RoundedCornerShape(4.dp)).padding(horizontal=4.dp, vertical=2.dp)) {
                                                Text("${markers.size} Defekte", color=Color.White, fontSize=10.sp, fontWeight=FontWeight.Bold)
                                            }
                                        }
                                        IconButton(
                                            onClick = { selectedImages = selectedImages.filter { it != uri } },
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.Close, "Löschen", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            Modifier.size(100.dp, 46.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                .clickable { launcher.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.Add, "Hinzufügen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Galerie", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Box(
                                            Modifier.size(100.dp, 46.dp).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                .clickable { 
                                                    try {
                                                        val uri = createImageUri(context)
                                                        if (uri != null) {
                                                            cameraUri = uri
                                                            cameraLauncher.launch(uri)
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Kamera konnte nicht gestartet werden.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.PhotoCamera, "Kamera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Foto", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                            Text("Tippe auf ein Foto, um Defekte zu markieren.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.weight(1f).height(100.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .clickable { launcher.launch("image/*") }.background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Galerie", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                    }
                                }
                                Box(
                                    Modifier.weight(1f).height(100.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                        .clickable { 
                                            try {
                                                val uri = createImageUri(context)
                                                if (uri != null) {
                                                    cameraUri = uri
                                                    cameraLauncher.launch(uri)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Kamera Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }.background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Foto machen", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (error != null) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                        }
                    }

                    // Built once so publishing and saving as a draft can't drift apart.
                    fun buildRequest(status: String?): CreateListingRequest {
                        val parsedPrice = price.replace(",", ".").toDoubleOrNull() ?: 0.0
                        return CreateListingRequest(
                            title = title, description = description, category = category,
                            subcategory = subcategory.ifEmpty { null },
                            price = parsedPrice, priceType = priceType,
                            condition = if (category !in listOf("Autos","Immobilien","Jobs")) condition else null,
                            location = location, shipping = shipping,
                            brand = brand.ifEmpty { null }, model = model.ifEmpty { null },
                            year = year.toIntOrNull(), mileage = mileage.toIntOrNull(),
                            fuelType = fuelType.ifEmpty { null }, transmission = transmission.ifEmpty { null },
                            power = power.toIntOrNull(), accidentFree = accidentFree,
                            propertyType = propertyType.ifEmpty { null },
                            squareMeters = sqm.toDoubleOrNull(), rooms = rooms.toDoubleOrNull(),
                            companyName = companyName.ifEmpty { null }, jobType = jobType.ifEmpty { null },
                            salary = salary.ifEmpty { null }, homeOffice = homeOffice,
                            imageMarkers = imageMarkers.ifEmpty { null },
                            color = color.ifEmpty { null }, modelVariant = modelVariant.ifEmpty { null },
                            registrationDate = registrationDate.ifEmpty { null },
                            owners = owners.toIntOrNull(), tuev = tuev.ifEmpty { null },
                            // "Erdgeschoss" and friends map to the numeric floor the schema stores.
                            floor = parseFloor(floor), totalFloors = totalFloors.toIntOrNull(),
                            heatingType = heatingType.ifEmpty { null }, energyClass = energyClass.ifEmpty { null },
                            availableFrom = availableFrom.ifEmpty { null },
                            furnished = furnished, balcony = balcony, elevator = elevator,
                            parking = parking, cellar = cellar, garden = garden,
                            startDate = startDate.ifEmpty { null },
                            requirements = requirements.ifEmpty { null }, benefits = benefits.ifEmpty { null },
                            serviceArea = serviceArea.ifEmpty { null }, availability = availability.ifEmpty { null },
                            experience = experience.ifEmpty { null }, priceUnit = priceUnit.ifEmpty { null },
                            ram = ram.ifEmpty { null }, storage = storage.ifEmpty { null },
                            warranty = warranty.ifEmpty { null },
                            eventDate = eventDate.ifEmpty { null }, eventTime = eventTime.ifEmpty { null },
                            eventFrequency = eventFrequency.ifEmpty { null },
                            eventAddress = eventAddress.ifEmpty { null },
                            coinDiscountPercent = coinDiscountPercent.toIntOrNull()?.coerceIn(0, 50),
                            coinDiscountMax = coinDiscountMax.toIntOrNull(),
                            status = status,
                        )
                    }

                    if (editListingId == null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.submitListing(
                                    context, buildRequest("draft"), selectedImages, null,
                                    onSuccess = { onCreated() },
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !isLoading && title.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Als Entwurf speichern", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            // Saving a draft publishes it; every other status stays untouched.
                            val status = if (editListing?.status == "draft") "active" else null
                            viewModel.submitListing(
                                context, buildRequest(status), selectedImages, editListingId,
                                onSuccess = { onCreated() },
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isLoading && title.isNotBlank() && description.isNotBlank() && location.isNotBlank() && price.isNotBlank(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        else Text(
                            when {
                                editListing?.status == "draft" -> "Entwurf veröffentlichen"
                                editListingId != null -> "Änderungen speichern"
                                else -> "Inserat veröffentlichen"
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }

        if (selectedImageForMarker != null) {
            var tappedPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
            var defectText by remember { mutableStateOf("") }
            val (imgIndex, imgUri) = selectedImageForMarker!!
            val currentMarkers = imageMarkers.filter { it.imageIndex == imgIndex }

            androidx.compose.ui.window.Dialog(onDismissRequest = { selectedImageForMarker = null }) {
                Surface(Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(16.dp), color = Color.Black) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = imgUri, contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* catch clicks if needed */ }
                        )
                        // The pointer input overlay to get exact relative coordinates
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {
                            val boxSize = this.size
                            detectTapGestures { offset ->
                                tappedPos = (offset.x / boxSize.width.toFloat()) to (offset.y / boxSize.height.toFloat())
                            }
                        })

                        val conf = androidx.compose.ui.platform.LocalConfiguration.current
                        // Draw existing markers
                        currentMarkers.forEach { m ->
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.align(Alignment.TopStart).offset(
                                    x = (m.x * conf.screenWidthDp).dp,
                                    y = (m.y * conf.screenHeightDp).dp
                                )) {
                                    Icon(Icons.Filled.Close, "Defekt", tint = Color.Red, modifier = Modifier.size(24.dp).background(Color.White, CircleShape))
                                }
                            }
                        }

                        // Tapped position logic
                        if (tappedPos != null) {
                            AlertDialog(
                                onDismissRequest = { tappedPos = null },
                                title = { Text("Defekt markieren") },
                                text = { OutlinedTextField(value = defectText, onValueChange = { defectText = it }, label = { Text("Beschreibung (z.B. Kratzer)") }) },
                                confirmButton = {
                                    Button(onClick = {
                                        if (defectText.isNotBlank()) {
                                            imageMarkers = imageMarkers + at.nimmdas.app.data.model.ImageMarker(imgIndex, tappedPos!!.first.toDouble(), tappedPos!!.second.toDouble(), defectText)
                                        }
                                        tappedPos = null
                                        defectText = ""
                                    }) { Text("Speichern") }
                                },
                                dismissButton = { TextButton(onClick = { tappedPos = null; defectText = "" }) { Text("Abbrechen") } }
                            )
                        }

                        IconButton(onClick = { selectedImageForMarker = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.White.copy(0.7f), CircleShape)) {
                            Icon(Icons.Filled.Close, "Schließen", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

/**
 * The website's quick-create hero: type what you're selling and the category is picked
 * for you. Tapping the result jumps straight into the form.
 */
@Composable
private fun QuickTitleBar(
    title: String,
    onTitleChange: (String) -> Unit,
    prediction: at.nimmdas.app.data.model.CategoryPrediction?,
    isPredicting: Boolean,
    onAccept: (at.nimmdas.app.data.model.CategoryPrediction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("Was möchtest du inserieren?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Tippe einfach deinen Titel ein – die Kategorie wird automatisch erkannt.",
            fontSize = 12.sp, lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            placeholder = { Text("z.B. iPhone 15 Pro Max 256GB", fontSize = 14.sp) },
            leadingIcon = {
                if (isPredicting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        when {
            isPredicting -> Text("Kategorie wird analysiert …", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary)

            prediction != null -> Surface(
                onClick = { onAccept(prediction) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Kategorie erkannt", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(
                            prediction.category + if (prediction.subcategory.isNotBlank()) " › ${prediction.subcategory}" else "",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            title.trim().length >= 2 -> Text(
                "Keine automatische Kategorie erkannt – wähle unten manuell aus.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("ODER KATEGORIE WÄHLEN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
    }
}

/**
 * The schema stores the floor as a number; the picker offers the same German labels as
 * the website, so map them the same way the web form does.
 */
private fun parseFloor(value: String): Int? = when {
    value.isBlank() -> null
    value.contains("Erdgeschoss", true) || value.contains("Parterre", true) -> 0
    value.contains("Keller", true) || value.contains("Souterrain", true) -> -1
    value.contains("Dachgeschoss", true) || value.contains("Penthouse", true) -> 99
    else -> Regex("\\d+").find(value)?.value?.toIntOrNull()
}

@Composable
fun FormField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), singleLine = true
    )
}

/** Free-text field for longer entries such as job requirements. */
@Composable
fun MultilineField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        minLines = 3, maxLines = 6,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, options: List<Any>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Handle both String lists and Pair<String, String> lists
    val displayValue = if (options.firstOrNull() is Pair<*,*>) {
        @Suppress("UNCHECKED_CAST")
        (options as List<Pair<String, String>>).find { it.first == value }?.second ?: value
    } else value

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = displayValue, onValueChange = {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                val (id, text) = if (opt is Pair<*,*>) opt.first.toString() to opt.second.toString() else opt.toString() to opt.toString()
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCompleteField(label: String, value: String, suggestions: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter { it.contains(value, ignoreCase = true) }.take(5)

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty() && value.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(label) },
            modifier = Modifier.menuAnchor().fillMaxWidth().padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        if (filtered.isNotEmpty() && value.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
