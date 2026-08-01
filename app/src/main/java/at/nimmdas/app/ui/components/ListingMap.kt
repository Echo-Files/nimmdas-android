package at.nimmdas.app.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Small OpenStreetMap view showing where a listing is — the app counterpart of the
 * website's MiniMap. Uses the same OSM tiles.
 */
@Composable
fun ListingMap(
    lat: Double,
    lng: Double,
    modifier: Modifier = Modifier,
    zoom: Double = 14.0,
) {
    val context = LocalContext.current

    // osmdroid needs a user agent and a cache path before the first tile request,
    // otherwise OSM rejects the requests.
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = context.cacheDir
        osmdroidTileCache = context.cacheDir.resolve("osmdroid")
    }

    var mapRef: MapView? = null

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                mapRef = this
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                controller.setCenter(GeoPoint(lat, lng))
                overlays.add(
                    Marker(this).apply {
                        position = GeoPoint(lat, lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = pinDrawable(ctx)
                    }
                )
            }
        },
        update = { map ->
            map.controller.setCenter(GeoPoint(lat, lng))
        },
    )

    // MapView keeps tile-loading threads alive; release them with the composable.
    DisposableEffect(Unit) {
        onDispose { runCatching { mapRef?.onDetach() } }
    }
}

/** Brand-coloured teardrop pin, matching the website marker. */
private fun pinDrawable(ctx: android.content.Context): BitmapDrawable {
    val size = (36 * ctx.resources.displayMetrics.density).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#10B981")
        c.drawCircle(r, r, r * 0.9f, this)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        c.drawCircle(r, r, r * 0.35f, this)
    }
    return BitmapDrawable(ctx.resources, bmp)
}
