package at.nimmdas.app.ui.screens.profile

import android.app.Application
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.SavedSearchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedSearchesViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _searches = MutableStateFlow<List<SavedSearchItem>>(emptyList())
    val searches = _searches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = apiClient.api.getSavedSearches()
                if (res.isSuccessful) {
                    _searches.value = res.body() ?: emptyList()
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                val res = apiClient.api.deleteSavedSearch(id)
                if (res.isSuccessful) {
                    _searches.value = _searches.value.filter { it.id != id }
                }
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSearchesScreen(
    onBack: () -> Unit,
    onCreateNew: () -> Unit = {},
    viewModel: SavedSearchesViewModel = viewModel()
) {
    val searches by viewModel.searches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meine Suchagenten", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Neuer Suchagent") },
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (searches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.NotificationsNone, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.15f))
                Spacer(Modifier.height(16.dp))
                Text("Keine Suchagenten", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Erstelle einen Suchagenten bei der Suche,\num über neue passende Inserate\nbenachrichtigt zu werden.",
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
                    Text("${searches.size} aktive Suchagenten", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), modifier = Modifier.padding(bottom = 4.dp))
                }
                items(searches) { search ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Notifications, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(search.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    search.category?.let {
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                            Text(it, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    search.query?.let {
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                            Text("\"$it\"", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    search.location?.let {
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                                            Text("📍 $it", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                                if (search.minPrice != null || search.maxPrice != null) {
                                    val priceStr = buildString {
                                        append("€ ")
                                        append(search.minPrice?.toInt()?.toString() ?: "0")
                                        append(" – ")
                                        append(search.maxPrice?.toInt()?.toString() ?: "∞")
                                    }
                                    Text(priceStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                }
                            }
                            IconButton(
                                onClick = { search.id?.let { viewModel.delete(it) } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, "Löschen", Modifier.size(20.dp), tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}
