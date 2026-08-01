package at.nimmdas.app.ui.screens.info

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private fun String.isVideo(): Boolean {
    val u = substringBefore('?').lowercase()
    return u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".mov") || u.endsWith(".m4v")
}

private fun String.isPdf(): Boolean = substringBefore('?').lowercase().endsWith(".pdf")

/**
 * In-app viewer for listing attachments.
 *
 * PDFs are downloaded and rendered with Android's own [PdfRenderer] — no third-party
 * viewer service sees the document. Videos play in a plain [VideoView]. Anything else
 * offers to open externally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember(url) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember(url) { mutableStateOf(url.isPdf()) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    // Download + render the PDF off the main thread.
    LaunchedEffect(url) {
        if (!url.isPdf()) return@LaunchedEffect
        loading = true; error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "doc_${url.hashCode()}.pdf")
                if (!file.exists() || file.length() == 0L) {
                    val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
                    response.use { r ->
                        if (!r.isSuccessful) error("HTTP ${r.code}")
                        file.outputStream().use { out -> r.body?.byteStream()?.copyTo(out) }
                    }
                }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        (0 until renderer.pageCount).map { i ->
                            renderer.openPage(i).use { page ->
                                // Render at ~2x for legible text without exploding memory.
                                val scale = 2
                                val bmp = Bitmap.createBitmap(
                                    page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }
            }
        }
        result.onSuccess { pages = it }.onFailure { error = "Dokument konnte nicht geladen werden" }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    }) { Icon(Icons.Filled.OpenInNew, "Extern öffnen") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF1A1A1A))) {
            when {
                url.isVideo() -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setVideoURI(android.net.Uri.parse(url))
                                setOnPreparedListener { it.isLooping = false; start() }
                            }
                        },
                    )
                }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Dokument wird geladen…", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    }
                }

                error != null || (url.isPdf() && pages.isEmpty()) -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error ?: "Vorschau nicht möglich", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url)
                                    )
                                )
                            }
                        }, shape = RoundedCornerShape(12.dp)) { Text("Extern öffnen") }
                    }
                }

                else -> {
                    // Pinch-zoomable page list
                    var scale by remember { mutableFloatStateOf(1f) }
                    LazyColumn(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                }
                            },
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(pages) { i, bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Seite ${i + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale),
                            )
                        }
                        if (pages.size > 1) {
                            item {
                                Text(
                                    "${pages.size} Seiten",
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    color = Color.White.copy(0.5f), fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
