package at.nimmdas.app.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import at.nimmdas.app.data.model.MapPin
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

/**
 * Search-result map with grid clustering.
 *
 * Markers are bucketed by screen position, so nearby listings collapse into one numbered
 * circle and the map stays readable even with hundreds of pins. Tapping a cluster zooms
 * into it; tapping a single pin reports the listing.
 *
 * Clustering is done here rather than via osmdroid-bonuspack to avoid pulling in a
 * JitPack dependency for what is a short bucketing loop.
 */
@Composable
fun ClusterMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    onPinClick: (MapPin) -> Unit,
) {
    val context = LocalContext.current
    // Held across recompositions so the dispose actually reaches the live MapView.
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    // The scroll/zoom listeners are installed once, so they must not capture the pin list
    // directly — otherwise panning rebuilds from the empty list the map started with.
    val pinsRef = rememberUpdatedState(pins)
    val clickRef = rememberUpdatedState(onPinClick)

    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = context.cacheDir
        osmdroidTileCache = context.cacheDir.resolve("osmdroid")
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                mapRef.value = this
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(7.0)
                controller.setCenter(GeoPoint(47.6, 13.5)) // Austria
                addMapListener(object : MapListener {
                    override fun onScroll(e: ScrollEvent?): Boolean {
                        rebuildClusters(this@apply, pinsRef.value, clickRef.value); return false
                    }
                    override fun onZoom(e: ZoomEvent?): Boolean {
                        rebuildClusters(this@apply, pinsRef.value, clickRef.value); return false
                    }
                })
            }
        },
        update = { map -> rebuildClusters(map, pins, onPinClick) },
    )

    DisposableEffect(Unit) { onDispose { runCatching { mapRef.value?.onDetach() } } }
}

/**
 * Screen-space cell size for bucketing, in dp. Must stay above the largest bubble
 * (62 dp) — with a smaller cell, neighbouring clusters are guaranteed to overlap.
 */
private const val CELL_DP = 76

private fun rebuildClusters(map: MapView, pins: List<MapPin>, onPinClick: (MapPin) -> Unit) {
    if (map.width == 0 || map.height == 0) return
    val projection = map.projection ?: return
    val cell = (CELL_DP * map.context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    // Bucket by screen cell so clusters visually separate at any zoom level.
    // Screen positions are kept too: a cluster is drawn at the middle of its own pins on
    // screen, not at the geographic mean, which used to drift out of the cell and collide.
    val buckets = HashMap<Long, MutableList<Pair<MapPin, android.graphics.Point>>>()
    for (p in pins) {
        val lat = p.lat ?: continue
        val lng = p.lng ?: continue
        val point = projection.toPixels(GeoPoint(lat, lng), null)
        // Skip far-offscreen pins so panning stays cheap.
        if (point.x < -cell || point.y < -cell ||
            point.x > map.width + cell || point.y > map.height + cell
        ) continue
        // floorDiv, not "/": integer division rounds towards zero, which would make the
        // cells around x=0 and y=0 twice as wide and merge unrelated clusters there.
        val key = Math.floorDiv(point.x, cell).toLong() * 100_000L + Math.floorDiv(point.y, cell).toLong()
        buckets.getOrPut(key) { mutableListOf() }.add(p to android.graphics.Point(point))
    }

    map.overlays.clear()
    for (group in buckets.values) {
        if (group.size == 1) {
            val p = group.first().first
            map.overlays.add(
                Marker(map).apply {
                    position = GeoPoint(p.lat!!, p.lng!!)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = singlePin(map.context)
                    title = p.title
                    setOnMarkerClickListener { _, _ -> onPinClick(p); true }
                }
            )
        } else {
            val cx = group.sumOf { it.second.x } / group.size
            val cy = group.sumOf { it.second.y } / group.size
            val centre = projection.fromPixels(cx, cy) as GeoPoint
            map.overlays.add(
                Marker(map).apply {
                    position = centre
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = clusterPin(map.context, group.size)
                    setOnMarkerClickListener { _, _ ->
                        map.controller.animateTo(centre, map.zoomLevelDouble + 2.0, 400L)
                        true
                    }
                }
            )
        }
    }
    map.invalidate()
}

/** Green teardrop for a single listing. */
private fun singlePin(ctx: android.content.Context): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (30 * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        c.drawCircle(r, r, r * 0.95f, this)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#10B981")
        c.drawCircle(r, r, r * 0.72f, this)
    }
    return BitmapDrawable(ctx.resources, bmp)
}

/** Numbered circle whose size grows with the count. */
private fun clusterPin(ctx: android.content.Context, count: Int): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val base = when {
        count < 10 -> 38
        count < 50 -> 46
        count < 200 -> 54
        else -> 62
    }
    val size = (base * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    // Soft halo, then solid core — reads clearly over map tiles.
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3310B981")
        c.drawCircle(r, r, r, this)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#059669")
        c.drawCircle(r, r, r * 0.76f, this)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        textSize = r * (if (count > 999) 0.5f else 0.62f)
        val label = if (count > 999) "999+" else count.toString()
        c.drawText(label, r, r - (descent() + ascent()) / 2f, this)
    }
    return BitmapDrawable(ctx.resources, bmp)
}

/** Rounds a zoom level for readable debug output. */
internal fun Double.zoomLabel(): String = roundToInt().toString()
