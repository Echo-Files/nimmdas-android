package at.nimmdas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.nimmdas.app.BuildConfig
import coil.compose.AsyncImage

/**
 * Fullscreen photo viewer: swipe between images, pinch to zoom, double-tap to toggle
 * zoom, tap the background or the ✕ to close.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullscreenGallery(
    images: List<String>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offsetX by remember(page) { mutableFloatStateOf(0f) }
                var offsetY by remember(page) { mutableFloatStateOf(0f) }

                val url = images[page].let {
                    if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
                }

                val zoomed = scale > 1f
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { if (!zoomed) onDismiss() },
                                onDoubleTap = {
                                    if (zoomed) { scale = 1f; offsetX = 0f; offsetY = 0f }
                                    else scale = 2.5f
                                },
                            )
                        }
                        // Hand-rolled instead of detectTransformGestures: that helper
                        // consumes every event, which would stop HorizontalPager from ever
                        // seeing a swipe. Here a single finger at 1× stays unconsumed, so
                        // swiping between photos keeps working, while pinches and drags
                        // while zoomed are handled and consumed.
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val pinching = pressed > 1

                                    if (pinching || scale > 1f) {
                                        if (zoomChange != 1f) {
                                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                                        }
                                        if (scale > 1f) {
                                            offsetX += panChange.x
                                            offsetY += panChange.y
                                        } else {
                                            offsetX = 0f; offsetY = 0f
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offsetX, translationY = offsetY,
                        ),
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) { Icon(Icons.Filled.Close, "Schließen", tint = Color.White) }

            if (images.size > 1) {
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(0.15f),
                ) {
                    Text(
                        "${pagerState.currentPage + 1}/${images.size}",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
