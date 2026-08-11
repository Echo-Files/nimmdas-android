package at.nimmdas.app.ui.screens.messages

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import at.nimmdas.app.data.model.MessageThread
import at.nimmdas.app.ui.components.timeAgo
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MessagesViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _threads = MutableStateFlow<List<MessageThread>>(emptyList())
    val threads = _threads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init { loadThreads() }

    fun loadThreads() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiClient.api.getThreads()
                if (response.isSuccessful) {
                    _threads.value = response.body() ?: emptyList()
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onThreadClick: (String) -> Unit,
    viewModel: MessagesViewModel = viewModel()
) {
    val threads by viewModel.threads.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // No TopAppBar here, so this screen carries the status-bar inset itself.
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Nachrichten", fontWeight = FontWeight.Bold) },
        )

        if (isLoading && threads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        } else if (threads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    Spacer(Modifier.height(12.dp))
                    Text("Keine Nachrichten", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Schreibe einem Verkäufer über ein Inserat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = 4.dp,
                    bottom = at.nimmdas.app.navigation.BottomBarSpace,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(threads) { thread ->
                    ThreadItem(
                        thread = thread,
                        onClick = {
                            // Navigate with partnerId_listingId as threadId
                            onThreadClick("${thread.partnerId}_${thread.listingId}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadItem(thread: MessageThread, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if ((thread.unread ?: 0) > 0)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if ((thread.unread ?: 0) > 0) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Partner avatar
            Box(modifier = Modifier.size(48.dp)) {
                if (thread.partnerAvatar != null) {
                    val avatarUrl = thread.partnerAvatar.let { if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it" }
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                (thread.partnerName?.take(1) ?: "").uppercase().ifBlank { "?" },
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
                // Unread badge
                if ((thread.unread ?: 0) > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).align(Alignment.TopEnd)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("${thread.unread}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        thread.partnerName ?: "Unbekannt",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if ((thread.unread ?: 0) > 0) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    thread.lastMessageTime?.let {
                        Text(
                            timeAgo(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // Listing title
                thread.listingTitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Last message preview
                Text(
                    thread.lastMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if ((thread.unread ?: 0) > 0) 0.8f else 0.5f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if ((thread.unread ?: 0) > 0) FontWeight.Medium else FontWeight.Normal
                )
            }

            // Listing thumbnail
            thread.listingImage?.let { img ->
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = if (img.startsWith("http")) img else "${BuildConfig.API_BASE_URL}$img",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}
