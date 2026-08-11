package at.nimmdas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.data.PriceRating
import at.nimmdas.app.data.evaluatePrice
import at.nimmdas.app.data.model.Listing
import androidx.compose.foundation.shape.CircleShape
import at.nimmdas.app.data.model.SellerInfo
import at.nimmdas.app.data.model.formatMeasure
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ListingCard(
    listing: Listing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewMode: String = "grid",  // "grid" or "list"
    /** Null hides the heart entirely (e.g. in the seller's own lists). */
    isSaved: Boolean? = null,
    onToggleSave: (() -> Unit)? = null,
) {
    val imageUrl = listing.images?.firstOrNull()?.let {
        if (it.startsWith("http")) it else "${BuildConfig.API_BASE_URL}$it"
    }

    val priceText = when (listing.priceType) {
        "verschenken" -> "Gratis"
        "auf_anfrage" -> "Auf Anfrage"
        "vb" -> "€ ${formatPrice(listing.price)} VB"
        else -> if (listing.price != null && listing.price > 0) "€ ${formatPrice(listing.price)}" else "Gratis"
    }

    val priceColor = when (listing.priceType) {
        "verschenken" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }

    // Category-specific chips (matching website)
    val chips = mutableListOf<String>()
    when (listing.category) {
        "Autos" -> {
            listing.year?.let { chips.add("$it") }
            listing.mileage?.let { chips.add("${it / 1000}tkm") }
            listing.power?.let { chips.add("${it}PS") }
            listing.fuelType?.let { chips.add(it) }
            listing.transmission?.let { chips.add(if (it == "Automatik") "Automatik" else "Schaltung") }
        }
        "Immobilien" -> {
            listing.squareMeters?.let { chips.add("${it.formatMeasure()}m²") }
            listing.rooms?.let { chips.add("${it.formatMeasure()}Zi") }
            listing.propertyType?.let { chips.add(if (it == "rent") "Miete" else "Kauf") }
        }
        "Jobs" -> {
            listing.jobType?.let { chips.add(it) }
            if (listing.homeOffice == true) chips.add("Remote")
            listing.salary?.let { chips.add(it) }
        }
    }
    if (listing.shipping == true && listing.category !in listOf("Immobilien", "Jobs")) {
        chips.add("📦 Versand")
    }

    val condBadge = when (listing.condition) {
        "neu" -> "Neu" to Color(0xFF10B981)
        "gebraucht" -> "Gebraucht" to Color(0xFF3B82F6)
        "defekt" -> "Defekt" to Color(0xFFEF4444)
        else -> null
    }

    val isNew = listing.createdAt?.let {
        try { System.currentTimeMillis() - parseDate(it) < 86400000 } catch (_: Exception) { false }
    } ?: false

    val isBoosted = listing.boostedUntil?.let {
        try { parseDate(it) > System.currentTimeMillis() } catch (_: Exception) { false }
    } ?: false

    // Price verdict ("Super Deal" / "Fairer Preis" / …), same rules as the website badge.
    val priceVerdict = remember(listing.id, listing.price, listing.estimatedUsedPrice) {
        evaluatePrice(listing)
    }
    // Real-estate listings come from agencies — showing who is behind the offer is the
    // main trust signal in that category, so the card carries logo + company name.
    val agency = listing.sellerId?.takeIf { s ->
        listing.category == "Immobilien" && s.name.isNotBlank()
    }

    val verdictColor = when (priceVerdict?.rating) {
        PriceRating.SUPER -> Color(0xFF059669)
        PriceRating.FAIR -> Color(0xFF2563EB)
        PriceRating.HIGH -> Color(0xFFD97706)
        PriceRating.OVER_NEW -> Color(0xFFE11D48)
        null -> Color.Unspecified
    }

    val isList = viewMode == "list"
    val maxChips = if (isList) 5 else 3

    Card(
        // Default tile width suits the horizontal rows on the home screen; a caller
        // placing cards in a grid passes fillMaxWidth(), which overrides it because the
        // caller's modifier is applied last.
        modifier = (if (!isList) Modifier.width(185.dp) else Modifier.fillMaxWidth())
            .then(modifier)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (isList) {
            // ═══ LIST / ROW VIEW ═══
            Row(Modifier.height(IntrinsicSize.Min)) {
                // Image (left)
                Box(
                    Modifier
                        .width(130.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = listing.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Text("📷", fontSize = 28.sp) }
                    }
                    // Save toggle
                    if (isSaved != null && onToggleSave != null) {
                        SaveHeart(isSaved, onToggleSave, Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                    // Badges
                    Column(Modifier.align(Alignment.TopStart).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (isBoosted) SmallBadge("⚡ Top", Color(0xFFD97706))
                        if (isNew && !isBoosted) SmallBadge("Neu", Color(0xFF10B981))
                        if (listing.status == "reserved") SmallBadge("Reserviert", Color(0xFFEAB308))
                    }
                    // Image count
                    val imageCount = listing.images?.size ?: 0
                    if (imageCount > 1) {
                        Surface(
                            color = Color.Black.copy(0.7f), shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                        ) {
                            Text("📷 $imageCount", Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Content (right)
                Column(Modifier.weight(1f).padding(12.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        // Title
                        Text(listing.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        // Price + condition
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(priceText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = priceColor)
                            condBadge?.let { (label, color) ->
                                Surface(color = color.copy(0.1f), shape = RoundedCornerShape(6.dp)) {
                                    Text(label, Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color)
                                }
                            }
                            priceVerdict?.let { v ->
                                Surface(color = verdictColor.copy(0.12f), shape = RoundedCornerShape(6.dp)) {
                                    Text(v.label, Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = verdictColor)
                                }
                            }
                        }
                        // Chips
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                chips.take(maxChips).forEach { chip ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                                    ) {
                                        Text(chip, Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                    }
                                }
                                if (chips.size > maxChips) {
                                    Text("+${chips.size - maxChips}", fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                        modifier = Modifier.align(Alignment.CenterVertically))
                                }
                            }
                        }
                    }
                    // Agency row (Immobilien)
                    agency?.let { AgencyRow(it) }
                    // Location + time (bottom)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        listing.location?.let { loc ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                                Spacer(Modifier.width(2.dp))
                                Text(loc, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontWeight = FontWeight.Medium)
                            }
                        }
                        listing.createdAt?.let {
                            // Without the gap a long place name runs straight into the age.
                            Spacer(Modifier.width(8.dp))
                            Text(timeAgo(it), fontSize = 11.sp, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        }
                    }
                }
            }
        } else {
            // ═══ GRID VIEW (original) ═══
            Column {
                // Image
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                ) {
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = listing.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center) { Text("📷", fontSize = 28.sp) }
                    }
                    Box(Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.2f)))))
                    // Save toggle
                    if (isSaved != null && onToggleSave != null) {
                        SaveHeart(isSaved, onToggleSave, Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                    Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isBoosted) SmallBadge("⚡ Top", Color(0xFFD97706))
                        if (isNew && !isBoosted) SmallBadge("Neu", Color(0xFF10B981))
                        if (listing.status == "reserved") SmallBadge("Reserviert", Color(0xFFEAB308))
                        if (listing.status == "sold") SmallBadge("Verkauft", Color(0xFF3B82F6))
                        if (listing.priceType == "verschenken") SmallBadge("Gratis", Color(0xFF10B981))
                    }
                    val imageCount = listing.images?.size ?: 0
                    if (imageCount > 1) {
                        Surface(color = Color.Black.copy(0.7f), shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                            Text("📷 $imageCount", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                // Content
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(priceText, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = priceColor, maxLines = 1)
                        condBadge?.let { (label, color) ->
                            Surface(color = color.copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text(label, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(listing.title, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (chips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            chips.take(3).forEach { chip ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(0.3f))
                                ) {
                                    Text(chip, Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        fontSize = 10.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                                }
                            }
                            if (chips.size > 3) {
                                Text("+${chips.size - 3}", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                    agency?.let { AgencyRow(it) }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        listing.location?.let { loc ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.LocationOn, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                                Spacer(Modifier.width(2.dp))
                                Text(loc, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontWeight = FontWeight.Medium)
                            }
                        }
                        listing.createdAt?.let {
                            // Without the gap a long place name runs straight into the age.
                            Spacer(Modifier.width(8.dp))
                            Text(timeAgo(it), fontSize = 11.sp, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallBadge(text: String, color: Color) {
    Surface(color = Color.White.copy(0.95f), shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.3.sp)
    }
}

fun formatPrice(price: Double?): String {
    if (price == null) return "0"
    return NumberFormat.getInstance(Locale("de", "AT")).format(price.toLong())
}

fun parseDate(dateString: String): Long {
    val formats = listOf(
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )
    for (fmt in formats) {
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        try { return fmt.parse(dateString)?.time ?: 0L } catch (_: Exception) {}
    }
    return 0L
}

fun timeAgo(dateString: String): String {
    return try {
        val date = parseDate(dateString)
        if (date == 0L) return dateString
        val diff = System.currentTimeMillis() - date
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        when {
            minutes < 2 -> "Gerade eben"
            minutes < 60 -> "Vor ${minutes}m"
            hours < 24 -> "Vor ${hours}h"
            days < 7 -> "Vor ${days}d"
            days < 30 -> "Vor ${days / 7}w"
            else -> "Vor ${days / 30}M"
        }
    } catch (_: Exception) { dateString }
}

/** Agency logo + company name, shown on real-estate cards. */
@Composable
private fun AgencyRow(seller: SellerInfo) {
    Spacer(Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(0.12f))
        ) {
            val logo = seller.avatar?.takeIf { it.isNotBlank() }
            if (logo != null) {
                AsyncImage(
                    model = if (logo.startsWith("http")) logo else "${BuildConfig.API_BASE_URL}$logo",
                    contentDescription = seller.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        seller.name.take(1).uppercase(), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            seller.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (seller.verified == true) Text("✅", fontSize = 9.sp)
    }
}

/**
 * Heart button overlaid on a card's image.
 *
 * A translucent scrim reads differently over every photo, so once saved the chip turns
 * solid white — that way the state is legible no matter what is behind it. The icon
 * springs slightly when toggled.
 */
@Composable
private fun SaveHeart(isSaved: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (isSaved) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heartScale",
    )
    Surface(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        shape = CircleShape,
        color = if (isSaved) Color.White else Color.Black.copy(0.38f),
        shadowElevation = if (isSaved) 3.dp else 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isSaved) "Gemerkt" else "Merken",
                modifier = Modifier.size(18.dp).scale(scale),
                tint = if (isSaved) Color(0xFFEF4444) else Color.White,
            )
        }
    }
}
