package at.nimmdas.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Placeholder blocks shown while content loads.
 *
 * A bare spinner says "something is happening"; a skeleton in the shape of the result
 * says what is coming, and the screen no longer jumps when the data lands.
 */
@Composable
private fun shimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + 380f, 260f),
    )
}

/** A single rounded placeholder block. */
@Composable
fun SkeletonBox(modifier: Modifier, corner: Int = 12) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(shimmerBrush()))
}

/** Placeholder in the shape of a listing tile. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, imageHeight: Int = 120) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 12.dp),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(imageHeight.dp), corner = 20)
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.55f).height(18.dp), corner = 6)
            SkeletonBox(Modifier.fillMaxWidth(0.9f).height(13.dp), corner = 6)
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(13.dp), corner = 6)
        }
    }
}

/** A horizontal row of tile placeholders, matching the home screen's carousels. */
@Composable
fun SkeletonRow(count: Int = 4) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(List(count) { it }) {
            SkeletonCard(Modifier.width(185.dp))
        }
    }
}

/** Placeholder list used by the search results while the first page loads. */
@Composable
fun SkeletonList(count: Int = 6) {
    Column(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(Modifier.size(96.dp), corner = 14)
                Column(
                    Modifier.weight(1f).padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBox(Modifier.fillMaxWidth(0.85f).height(16.dp), corner = 6)
                    SkeletonBox(Modifier.fillMaxWidth(0.45f).height(20.dp), corner = 6)
                    SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp), corner = 6)
                }
            }
        }
    }
}
