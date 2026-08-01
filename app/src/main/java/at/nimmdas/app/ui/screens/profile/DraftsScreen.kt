package at.nimmdas.app.ui.screens.profile

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.Listing
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DraftsViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _drafts = MutableStateFlow<List<Listing>>(emptyList())
    val drafts = _drafts.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = apiClient.api.getMyListings()
                if (res.isSuccessful) {
                    _drafts.value = (res.body() ?: emptyList()).filter { it.status == "draft" }
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.deleteListing(id)
                if (res.isSuccessful) {
                    _drafts.value = _drafts.value.filter { it.id != id }
                }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onEditDraft: (String) -> Unit = {},
    viewModel: DraftsViewModel = viewModel()
) {
    val drafts by viewModel.drafts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meine Entwürfe", fontWeight = FontWeight.Bold) },
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
        } else if (drafts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Description, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.15f))
                Spacer(Modifier.height(16.dp))
                Text("Keine Entwürfe", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Entwürfe werden erstellt, wenn du\nein Inserat speicherst ohne zu veröffentlichen.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${drafts.size} Entwürfe gespeichert", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                items(drafts) { draft ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Image
                            val imgUrl = draft.images?.firstOrNull()?.let {
                                if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                            }
                            if (imgUrl != null) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Description, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Info
                            Column(Modifier.weight(1f)) {
                                Text(
                                    draft.title.ifBlank { "Ohne Titel" },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${draft.category ?: "Kategorie"} · ${draft.createdAt?.take(10) ?: ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                )
                            }

                            // Actions
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    onClick = { onEditDraft(draft.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(0.1f)
                                ) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Edit, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Bearbeiten", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.deleteDraft(draft.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, "Löschen", Modifier.size(18.dp), tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
