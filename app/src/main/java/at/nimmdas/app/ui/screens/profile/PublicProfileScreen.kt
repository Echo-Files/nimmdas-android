package at.nimmdas.app.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.*
import at.nimmdas.app.ui.components.ListingCard
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PublicProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _user = MutableStateFlow<PublicUser?>(null)
    val user = _user.asStateFlow()

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings = _listings.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing = _isFollowing.asStateFlow()

    private val _ratings = MutableStateFlow<RatingsResponse?>(null)
    val ratings = _ratings.asStateFlow()

    private val _ratingSubmitted = MutableStateFlow(false)
    val ratingSubmitted = _ratingSubmitted.asStateFlow()

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiClient.api.getUserProfile(userId)
                if (response.isSuccessful) {
                    val data = response.body()
                    _user.value = data?.user
                    _listings.value = data?.listings ?: emptyList()
                    _isFollowing.value = data?.isFollowing ?: false
                }
                // Load ratings
                try {
                    val ratingsRes = apiClient.api.getRatings(userId)
                    if (ratingsRes.isSuccessful) {
                        _ratings.value = ratingsRes.body()
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun toggleFollow(userId: String) {
        viewModelScope.launch {
            try {
                if (_isFollowing.value) {
                    val res = apiClient.api.unfollowUser(userId)
                    if (res.isSuccessful) {
                        _isFollowing.value = false
                        _user.value = _user.value?.copy(followerCount = res.body()?.followerCount ?: (_user.value?.followerCount?.minus(1) ?: 0))
                    }
                } else {
                    val res = apiClient.api.followUser(userId)
                    if (res.isSuccessful) {
                        _isFollowing.value = true
                        _user.value = _user.value?.copy(followerCount = res.body()?.followerCount ?: (_user.value?.followerCount?.plus(1) ?: 0))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun submitRating(toUserId: String, stars: Int, comment: String?) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.createRating(CreateRatingRequest(toUserId, stars, comment))
                if (res.isSuccessful) {
                    _ratingSubmitted.value = true
                    // Reload ratings
                    val ratingsRes = apiClient.api.getRatings(toUserId)
                    if (ratingsRes.isSuccessful) {
                        _ratings.value = ratingsRes.body()
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onListingClick: (String) -> Unit,
    viewModel: PublicProfileViewModel = viewModel()
) {
    val user by viewModel.user.collectAsState()
    val listings by viewModel.listings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val ratingSubmitted by viewModel.ratingSubmitted.collectAsState()

    var showRatingDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: "Profil", fontWeight = FontWeight.Bold) },
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
        } else if (user == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Benutzer nicht gefunden")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Snapshot the avatar once — `user` is a StateFlow delegate, so a
                        // null-check and a later read are two separate reads of a value
                        // that can change in between.
                        val avatar = user?.avatar
                        Box(Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)) {
                            if (avatar != null) {
                                val avatarUrl = avatar.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        user?.name?.take(1)?.uppercase() ?: "?",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user?.name ?: "Unbekannt", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (user?.verified == true) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.CheckCircle, "Verifiziert", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (!user?.location.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Spacer(Modifier.width(4.dp))
                                Text(user?.location ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            }
                        }

                        if (!user?.bio.isNullOrBlank()) {
                            Text(
                                text = user?.bio ?: "",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Rating Stars — snapshot once, see the avatar note above.
                        val ratingSummary = ratings
                        if (ratingSummary != null && ratingSummary.count > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                repeat(5) { i ->
                                    Icon(
                                        if (i < (ratingSummary.average + 0.5).toInt()) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = Color(0xFFFBBF24)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("${ratingSummary.average}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(" (${ratingSummary.count})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }

                        // Stats
                        Row(
                            Modifier.fillMaxWidth().padding(top = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatItem("Inserate", listings.size.toString())
                            ProfileStatItem("Follower", user?.followerCount?.toString() ?: "0")
                            ProfileStatItem("Aktiv seit", user?.createdAt?.take(4) ?: "-")
                        }

                        Spacer(Modifier.height(16.dp))

                        // Follow + Rate buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.toggleFollow(userId) },
                                shape = RoundedCornerShape(50),
                                colors = if (isFollowing) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                         else ButtonDefaults.buttonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                                    null, Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isFollowing) "Entfolgen" else "Folgen",
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { showRatingDialog = true },
                                shape = RoundedCornerShape(50),
                                enabled = !ratingSubmitted
                            ) {
                                Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = Color(0xFFFBBF24))
                                Spacer(Modifier.width(4.dp))
                                Text(if (ratingSubmitted) "Bewertet" else "Bewerten", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Ratings Section
                val ratingList = ratings
                if (ratingList != null && ratingList.ratings.isNotEmpty()) {
                    item {
                        Text("Bewertungen (${ratingList.count})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    items(ratingList.ratings.take(5)) { rating ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(0.5.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (rating.fromUserId?.avatar != null) {
                                            val av = rating.fromUserId.avatar.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                                            AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        } else {
                                            Text(rating.fromUserId?.name?.take(1)?.uppercase() ?: "?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(rating.fromUserId?.name ?: "Anonym", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    Row {
                                        repeat(rating.stars) {
                                            Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                                        }
                                    }
                                }
                                if (!rating.comment.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(rating.comment, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                }
                            }
                        }
                    }
                }

                // Listings
                item {
                    Text("Inserate von ${user?.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }

                items(listings) { listing ->
                    ListingCard(
                        listing = listing,
                        onClick = { onListingClick(listing.id) },
                        viewMode = "list"
                    )
                }
            }
        }
    }

    // Rating Dialog
    if (showRatingDialog) {
        RatingDialog(
            onDismiss = { showRatingDialog = false },
            onSubmit = { stars, comment ->
                viewModel.submitRating(userId, stars, comment)
                showRatingDialog = false
            }
        )
    }
}

@Composable
private fun ProfileStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}

@Composable
private fun RatingDialog(
    onDismiss: () -> Unit,
    onSubmit: (Int, String?) -> Unit
) {
    var stars by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bewertung abgeben", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Wie zufrieden bist du mit diesem Verkäufer?", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(5) { i ->
                        IconButton(onClick = { stars = i + 1 }) {
                            Icon(
                                if (i < stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                                null,
                                Modifier.size(36.dp),
                                tint = Color(0xFFFBBF24)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kommentar (optional)") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(stars, comment.ifBlank { null }) },
                enabled = stars > 0,
                shape = RoundedCornerShape(50)
            ) {
                Text("Bewerten", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
