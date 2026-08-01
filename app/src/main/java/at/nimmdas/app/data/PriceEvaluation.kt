package at.nimmdas.app.data

import at.nimmdas.app.data.model.Listing

/**
 * Mirrors the web `PriceEvaluationBadge` logic so the app rates prices identically.
 *
 * Cars are compared straight against the estimated used-car market value; every other
 * category adjusts the used price by condition and caps it below the new price.
 */
enum class PriceRating { SUPER, FAIR, HIGH, OVER_NEW }

data class PriceVerdict(
    val rating: PriceRating,
    val label: String,
    /** The price we consider fair — shown in the detail view. */
    val fairPrice: Double,
    val estimatedNewPrice: Double?,
    /**
     * Where the listing price sits on the 0..1 gauge, using the same piecewise mapping
     * as the website so both render the marker at the same spot.
     */
    val gaugePosition: Float,
    /** True for cars, which use a simpler market-value comparison. */
    val isCar: Boolean,
)

/**
 * Maps a price onto the four coloured zones of the gauge:
 * green 0–45 % (super), blue 45–70 % (fair), amber 70–90 % (pricey), red 90–100 %.
 */
private fun gaugePosition(price: Double, fair: Double, newPrice: Double): Float {
    val pct = when {
        price < fair * 0.85 -> 5 + (price / (fair * 0.85)) * 40
        price <= fair * 1.15 -> {
            val start = fair * 0.85; val end = fair * 1.15
            45 + ((price - start) / (end - start)) * 25
        }
        price < newPrice * 0.95 -> {
            val start = fair * 1.15; val end = newPrice * 0.95
            70 + ((price - start) / maxOf(end - start, 1.0)) * 20
        }
        else -> 90 + ((price - newPrice * 0.95) / maxOf(newPrice * 0.15, 1.0)) * 5
    }
    return (pct.coerceIn(5.0, 95.0) / 100.0).toFloat()
}

private const val CACHE_VALID_DAYS = 7L
private val EXCLUDED_CATEGORIES = setOf("Autos", "Immobilien", "Jobs")

private fun conditionMultiplier(condition: String?): Double = when (condition?.lowercase()) {
    "neu" -> 3.0
    "neuwertig" -> 2.5
    "gebraucht" -> 1.0
    "defekt" -> 0.5
    else -> 1.0
}

/** Cached evaluations older than a week are treated as missing, like on the web. */
private fun isCacheFresh(updated: String?): Boolean {
    if (updated.isNullOrBlank()) return false
    return try {
        val ts = java.time.Instant.parse(
            if (updated.endsWith("Z") || updated.contains("+")) updated else updated + "Z"
        ).toEpochMilli()
        System.currentTimeMillis() - ts < CACHE_VALID_DAYS * 24 * 60 * 60 * 1000
    } catch (_: Exception) {
        // Unparsable timestamps still carry usable numbers; don't discard the evaluation.
        true
    }
}

/**
 * Returns the price verdict for a listing, or null when it cannot be rated
 * (missing data, excluded category, or a non-monetary price type).
 */
fun evaluatePrice(listing: Listing): PriceVerdict? {
    val price = listing.price ?: return null
    if (price <= 0) return null
    val used = listing.estimatedUsedPrice ?: return null
    if (used <= 0) return null
    if (!isCacheFresh(listing.priceEvaluationUpdated)) return null

    if (listing.category == "Autos") {
        // Cars need brand/model for the comparison to be meaningful.
        if (listing.brand.isNullOrBlank() || listing.model.isNullOrBlank()) return null
        val diffPercent = (price - used) / used * 100
        val rating = when {
            diffPercent <= -5 -> PriceRating.SUPER
            diffPercent <= 10 -> PriceRating.FAIR
            else -> PriceRating.HIGH
        }
        // Cars use a linear -30 %..+30 % scale around the market value.
        val pos = ((50 + (diffPercent / 30) * 33).coerceIn(5.0, 95.0) / 100.0).toFloat()
        return PriceVerdict(rating, rating.label(), used, listing.estimatedNewPrice, pos, isCar = true)
    }

    if (listing.category in EXCLUDED_CATEGORIES) return null
    if (listing.priceType == "auf_anfrage" || listing.priceType == "verschenken") return null

    val newPrice = listing.estimatedNewPrice ?: return null
    if (newPrice <= 0) return null

    val adjustedUsed = used * conditionMultiplier(listing.condition)
    val fair = minOf(adjustedUsed, newPrice * 0.9)
    val rating = when {
        price < fair * 0.85 -> PriceRating.SUPER
        price <= fair * 1.15 -> PriceRating.FAIR
        price < newPrice * 0.95 -> PriceRating.HIGH
        else -> PriceRating.OVER_NEW
    }
    return PriceVerdict(
        rating, rating.label(), fair, newPrice,
        gaugePosition(price, fair, newPrice), isCar = false,
    )
}

fun PriceRating.label(): String = when (this) {
    PriceRating.SUPER -> "Super Deal"
    PriceRating.FAIR -> "Fairer Preis"
    PriceRating.HIGH -> "Etwas teuer"
    PriceRating.OVER_NEW -> "Über Neupreis"
}
