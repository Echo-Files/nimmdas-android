package at.nimmdas.app.data.model

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val action: String? = null, // "login" or "register" or "google"
    val email: String? = null,
    val password: String? = null,
    val name: String? = null,
    val idToken: String? = null, // Google ID token
    val referralCode: String? = null // Referral code from invite link
)

data class AuthResponse(
    val token: String,
    val user: UserInfo
)

data class UserInfo(
    @SerializedName("_id") val mongoId: String? = null,
    val id: String? = null,
    val name: String = "",
    val email: String? = null,
    val role: String? = "user",
    val avatar: String? = null,
    val coins: Int? = 0,
    val referralCode: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val verified: Boolean? = false,
    val createdAt: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val websiteApproved: Boolean? = null,
    val dealerAddress: String? = null,
    val dealerUid: String? = null,
    val whatsapp: Boolean? = null,
    val emailNotifications: Boolean? = null,
    val newsletterOptIn: Boolean? = null,
    val detailStatsUnlocked: Boolean? = null,
) {
    fun resolvedId(): String = id ?: mongoId ?: ""

    /** Dealers get the extra business fields on the edit screen. */
    fun isDealer(): Boolean = role in listOf("cardealer", "immodealer", "flohmarktdealer")
}

data class MeResponse(
    val user: UserInfo,
    val stats: UserStats? = null
)

/** Where a listing's views came from — shown in the detail statistics. */
data class TrafficSources(
    val direct: Int = 0,
    val search: Int = 0,
    val google: Int = 0,
    val social: Int = 0,
    val other: Int = 0,
) {
    fun total(): Int = direct + search + google + social + other
}

data class UserStats(
    val listings: Int? = 0,
    val totalListings: Int? = 0,
    val views: Int? = 0,
    val followers: Int? = 0,
    val coins: Int? = 0
)

data class Listing(
    @SerializedName("_id") val id: String = "",
    val title: String = "",
    val description: String? = null,
    val price: Double? = null,
    val priceType: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val condition: String? = null,
    val images: List<String>? = null,
    val location: String? = null,
    val status: String? = "active",
    val createdAt: String? = null,
    val views: Int? = 0,
    val trafficSources: TrafficSources? = null,
    val boostedUntil: String? = null,
    val bumpedAt: String? = null,
    val sellerId: SellerInfo? = null,
    val tags: List<String>? = null,
    val originalPrice: Double? = null,
    val coinDiscountPercent: Int? = null,
    val coinDiscountMax: Double? = null,
    // Price evaluation (cached server-side, refreshed weekly)
    val estimatedNewPrice: Double? = null,
    val estimatedUsedPrice: Double? = null,
    val priceEvaluationUpdated: String? = null,
    // Auto fields
    val brand: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val mileage: Int? = null,
    val power: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val color: String? = null,
    val material: String? = null,
    val registrationDate: String? = null,
    val tuev: String? = null,
    val owners: Int? = null,
    val modelVariant: String? = null,
    val ram: String? = null,
    val storage: String? = null,
    val warranty: String? = null,
    val eventDate: String? = null,
    val eventTime: String? = null,
    val eventFrequency: String? = null,
    val eventAddress: String? = null,
    val accidentFree: Boolean? = null,
    val equipment: List<String>? = null,
    // Immobilien — squareMeters and rooms carry fractional values in real data
    // (e.g. 104.07 m², 3.5 Zimmer); parsing them as Int breaks the whole response.
    val squareMeters: Double? = null,
    val rooms: Double? = null,
    val propertyType: String? = null,
    val floor: Int? = null,
    val totalFloors: Int? = null,
    val heatingType: String? = null,
    val energyClass: String? = null,
    // Energieausweis (Austrian energy certificate) — the values actually present in the
    // data; `energyClass` above is a legacy field that almost no listing carries.
    val hwbValue: Double? = null,
    val hwbClass: String? = null,
    val fgeeValue: Double? = null,
    val fgeeClass: String? = null,
    /** Attached PDFs — floor plans, brochures, zoning documents. */
    val documents: List<ListingDocument>? = null,
    val availableFrom: String? = null,
    val furnished: Boolean? = null,
    val balcony: Boolean? = null,
    val elevator: Boolean? = null,
    val parking: Boolean? = null,
    val cellar: Boolean? = null,
    val garden: Boolean? = null,
    // Contact person (real-estate imports carry a dedicated agent per listing)
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val contactPhoto: String? = null,
    // Geo position — [longitude, latitude]
    val coordinates: GeoPoint? = null,
    // Jobs
    val jobType: String? = null,
    val salary: String? = null,
    val homeOffice: Boolean? = null,
    val companyName: String? = null,
    val startDate: String? = null,
    val requirements: String? = null,
    val benefits: String? = null,
    // Dienstleistungen
    val serviceArea: String? = null,
    val availability: String? = null,
    val experience: String? = null,
    val priceUnit: String? = null,
    // Misc
    val shipping: Boolean? = null
)

data class ListingDocument(
    val title: String? = null,
    val url: String? = null,
)

/** GeoJSON point as stored by the backend: coordinates are [longitude, latitude]. */
data class GeoPoint(
    val type: String? = null,
    val coordinates: List<Double>? = null,
) {
    fun lat(): Double? = coordinates?.getOrNull(1)
    fun lng(): Double? = coordinates?.getOrNull(0)
}

data class SellerInfo(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val location: String? = null,
    val verified: Boolean? = false,
    val createdAt: String? = null,
    val lastOnline: String? = null,
    val responseTime: Int? = null,
    val followers: List<String>? = null,
    val premiumBadgeUntil: String? = null,
    /** Only shown once moderation approved it. */
    val profileBanner: String? = null,
    val bannerApproved: Boolean? = null,
)

data class ListingsResponse(
    val listings: List<Listing>,
    val total: Int? = null,
    val stats: HomeStats? = null
)

data class SearchResponse(
    val listings: List<Listing>,
    val total: Int? = 0,
    val page: Int? = 1,
    val totalPages: Int? = 1
)

data class HomeStats(
    val listingCount: Int? = 0,
    val userCount: Int? = 0
)

// ── Messages (matching /api/mobile/messages response) ──

data class MessageThread(
    val threadId: String? = null,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val partnerAvatar: String? = null,
    val listingId: String? = null,
    val listingTitle: String? = null,
    val listingImage: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: String? = null,
    val unread: Int? = 0
)

data class Message(
    @SerializedName("_id") val id: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    val content: String? = null,
    val timestamp: String? = null,
    val messageType: String? = "text",
    val read: Boolean? = false,
    val image: String? = null,
    val priceOffer: Double? = null,
    /** "pending", "accepted" or "declined" — only set on price_offer / coin_redeem. */
    val offerStatus: String? = null,
    val relatedMessageId: String? = null,
    /** Length of a finished call, in seconds. */
    val callDuration: Int? = null,
    val document: MessageDocument? = null,
    val location: MessageLocation? = null,
)

data class MessageDocument(
    val url: String = "",
    val name: String = "Dokument",
    val size: Long = 0,
    val mimeType: String = "application/pdf",
)

data class MessageLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val label: String = "",
)

data class SendMessageRequest(
    val content: String = "",
    val listingId: String? = null,
    val receiverId: String? = null,
    val messageType: String? = null,
    val priceOffer: Double? = null,
    val image: String? = null,
    val document: MessageDocument? = null,
    val location: MessageLocation? = null,
)

/** Accept or decline a price offer / coin redemption. */
data class OfferResponseRequest(
    val messageId: String = "",
    /** "accept" or "decline". */
    val action: String = "",
    val markAsSold: Boolean? = null,
)

data class ChatUploadResponse(
    val url: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val mimeType: String? = null,
    val type: String? = null,
)

data class CreateListingRequest(
    val title: String = "",
    val description: String = "",
    val price: Double? = null,
    val priceType: String = "",
    val category: String = "",
    val subcategory: String? = null,
    val condition: String? = null,
    val location: String? = null,
    val images: List<String>? = null,
    val shipping: Boolean? = false,
    val tags: List<String>? = null,
    val color: String? = null,
    val material: String? = null,
    val imageMarkers: List<ImageMarker>? = null,
    // Auto
    val brand: String? = null,
    val model: String? = null,
    val modelVariant: String? = null,
    val year: Int? = null,
    val mileage: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val power: Int? = null,
    val accidentFree: Boolean? = null,
    val equipment: List<String>? = null,
    // Immobilien
    val squareMeters: Double? = null,
    val rooms: Double? = null,
    val propertyType: String? = null,
    // Jobs
    val companyName: String? = null,
    val jobType: String? = null,
    val salary: String? = null,
    val homeOffice: Boolean? = null,
    // Autos
    val registrationDate: String? = null,
    val owners: Int? = null,
    val tuev: String? = null,
    // Immobilien
    val floor: Int? = null,
    val totalFloors: Int? = null,
    val heatingType: String? = null,
    val energyClass: String? = null,
    val availableFrom: String? = null,
    val furnished: Boolean? = null,
    val balcony: Boolean? = null,
    val elevator: Boolean? = null,
    val parking: Boolean? = null,
    val cellar: Boolean? = null,
    val garden: Boolean? = null,
    // Jobs
    val startDate: String? = null,
    val requirements: String? = null,
    val benefits: String? = null,
    // Dienstleistungen
    val serviceArea: String? = null,
    val availability: String? = null,
    val experience: String? = null,
    val priceUnit: String? = null,
    // Elektronik & Co.
    val ram: String? = null,
    val storage: String? = null,
    val warranty: String? = null,
    val accessories: List<String>? = null,
    // Events & Märkte
    val eventDate: String? = null,
    val eventTime: String? = null,
    val eventFrequency: String? = null,
    val eventAddress: String? = null,
    // Münzrabatt
    val coinDiscountPercent: Int? = null,
    val coinDiscountMax: Int? = null,
    /** "draft" parks the listing in the drafts list instead of publishing it. */
    val status: String? = null,
)

/** Result of the title-based category guess used by the quick-create bar. */
data class CategoryPrediction(
    val category: String = "",
    val subcategory: String = "",
    val brand: String = "",
    val model: String = "",
)

data class ImageMarker(
    val imageIndex: Int = 0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val label: String = ""
)

// ── Coins ──

data class CoinData(
    val coins: Int = 0,
    val canClaimDaily: Boolean = false,
    val loginStreak: Int = 0,
    val canSpin: Boolean = false,
    val scratchCardsRemaining: Int = 0,
    val gameStates: GameStates? = null,
    val transactions: List<CoinTransaction> = emptyList(),
    val referralCode: String? = null,
    val activePerks: ActivePerks? = null
)

data class ActivePerks(
    val premiumBadgeUntil: String? = null,
    val detailStatsUnlocked: Boolean = false,
    val boostedListings: List<ListingOverview> = emptyList(),
    val bumpedListings: List<ListingOverview> = emptyList()
)

data class ListingOverview(
    @SerializedName("_id") val id: String = "",
    val title: String = "",
    val boostedUntil: String? = null,
    val bumpedAt: String? = null
)


data class GameStates(
    val canTreasure: Boolean = false,
    val slotSpinsRemaining: Int = 0,
    val canMemory: Boolean = false,
    val tossesRemaining: Int = 0
)

data class CoinTransaction(
    @SerializedName("_id") val id: String? = null,
    val amount: Int = 0,
    val description: String = "",
    val createdAt: String? = null
)

data class CoinActionRequest(
    val action: String = "",
    val listingId: String? = null,
    val choice: String? = null
)

data class CoinActionResponse(
    val success: Boolean = false,
    val reward: Int? = null,
    val earned: Int? = null,
    val streak: Int? = null,
    val weeklyBonus: Int? = null,
    val newBalance: Int? = null,
    val remaining: Int? = null,
    val error: String? = null,
    // Slot
    val result: List<String>? = null,
    val isWin: Boolean? = null,
    val netAmount: Int? = null,
    val spinsRemaining: Int? = null,
    // Toss
    val outcome: String? = null,
    val tossesRemaining: Int? = null
)

data class ApiError(
    val error: String = ""
)

// ── NLP Smart Intent Response (from /api/search/smart-intent) ──
@Suppress("PropertyName")
data class SmartIntentResponse(
    val q_clean: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val location: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val condition: String? = null,
    val color: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val rooms: Double? = null,
    val sqmMin: Double? = null,
    val shipping: Boolean? = null,
    val priceType: String? = null,
    val jobType: String? = null,
    val radius: Int? = null,
    val sort: String? = null,
    val year: Int? = null,
    val mileageMax: Int? = null,
    val powerMin: Int? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val propertyType: String? = null,
    val material: String? = null,
    val clothingSize: String? = null,
    val gender: String? = null,
    val ram: String? = null,
    val storage: String? = null
)

// ── Watchlist ─────────────────────────────────
data class WatchlistResponse(val savedListings: List<Listing>)
data class WatchlistRequest(val listingId: String = "")
data class WatchlistToggleResponse(val saved: Boolean = false, val count: Int = 0)

// ── Profile Edit ──────────────────────────────
data class ProfileUpdateRequest(
    val name: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val website: String? = null,
    val dealerAddress: String? = null,
    val dealerUid: String? = null,
    val whatsapp: Boolean? = null,
    val emailNotifications: Boolean? = null,
    val newsletterOptIn: Boolean? = null,
)

// ── Listing Management ────────────────────────
data class StatusUpdateRequest(val status: String = "")

// ── Public User Profile ───────────────────────
data class UserProfileResponse(
    val user: PublicUser = PublicUser(),
    val listings: List<Listing> = emptyList(),
    val listingCount: Int = 0,
    val isFollowing: Boolean = false
)

data class PublicUser(
    val id: String? = null,
    @SerializedName("_id") val mongoId: String? = null,
    val name: String = "",
    val bio: String? = null,
    val avatar: String? = null,
    val verified: Boolean = false,
    val createdAt: String? = null,
    val lastOnline: String? = null,
    val responseTime: Int? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val isPremium: Boolean = false,
    val location: String? = null
) {
    fun resolvedId(): String = id ?: mongoId ?: ""
}

// ── Reports ───────────────────────────────────
data class ReportRequest(
    val listingId: String = "",
    val reason: String = "",
    val description: String? = null
)

// ── Upload ────────────────────────────────────
data class UploadResponse(
    val urls: List<String> = emptyList()
)

// ── Follow ────────────────────────────────────
data class FollowResponse(
    val success: Boolean = false,
    val isFollowing: Boolean = false,
    val followerCount: Int = 0
)

// ── Ratings ───────────────────────────────────
data class RatingsResponse(
    val ratings: List<RatingItem> = emptyList(),
    val average: Double = 0.0,
    val count: Int = 0
)

data class RatingItem(
    @SerializedName("_id") val id: String? = null,
    val fromUserId: RatingUser? = null,
    val toUserId: String? = null,
    val stars: Int = 0,
    val comment: String? = null,
    val createdAt: String? = null
)

data class RatingUser(
    @SerializedName("_id") val id: String? = null,
    val name: String = "",
    val avatar: String? = null
)

data class CreateRatingRequest(
    val toUserId: String = "",
    val stars: Int = 0,
    val comment: String? = null
)

// ── Saved Searches (Suchagenten) ──────────────
data class SavedSearchItem(
    @SerializedName("_id") val id: String? = null,
    val userId: String? = null,
    val name: String = "",
    val query: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val condition: String? = null,
    val location: String? = null,
    val radius: Int? = null,
    val shipping: Boolean? = null,
    val createdAt: String? = null
)

data class CreateSavedSearchRequest(
    val name: String = "",
    val query: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val condition: String? = null,
    val location: String? = null,
    val radius: Int? = null,
    val shipping: Boolean? = null
)

// ── Avatar ────────────────────────────────────
data class AvatarResponse(
    val url: String = ""
)

// ── Call API Models ──
data class CreateCallRequest(
    val receiverId: String = "",
    val listingId: String? = null,
    val offerSDP: String = ""
)

data class CreateCallResponse(
    val callId: String = ""
)

data class IncomingCallResponse(
    val incomingCall: CallData?
)

data class CallData(
    @SerializedName("_id") val id: String = "",
    val callerId: UserInfo? = null,
    val receiverId: String = "",
    val listingId: String? = null,
    val status: String = "",
    val offerSDP: String = "",
    val answerSDP: String? = null,
    val startedAt: String = ""
)

data class CallStatusResponse(
    val status: String = "",
    val answerSDP: String?
)

data class UpdateCallRequest(
    val status: String = "",
    val answerSDP: String? = null
)

data class IceCandidateData(
    val candidate: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sender: String? = null
)

data class IceCandidatesResponse(
    val candidates: List<IceCandidateData> = emptyList()
)

/**
 * Renders a measurement without a pointless ".0" — 104.07 stays "104.07", 80.0 becomes "80".
 * Used for squareMeters/rooms, which are fractional in the data but usually whole numbers.
 */
fun Double.formatMeasure(): String =
    if (this == kotlin.math.truncate(this)) this.toLong().toString() else this.toString()

// ── Live search preview (/api/search/preview) ──
data class SearchPreviewResponse(
    val results: List<SearchPreviewItem> = emptyList()
)

data class SearchPreviewItem(
    @SerializedName("_id") val id: String = "",
    val title: String = "",
    val price: Double? = null,
    val priceType: String? = null,
    val image: String? = null,
    val location: String? = null,
    val category: String? = null
)

// ── "Auf gut Glück" (/api/search/lucky) — returns a single listing id ──
data class LuckyResponse(val id: String? = null)

// ── Location autocomplete (/api/locations/autocomplete) ──
data class LocationSuggestion(
    /** "3143 Wieden" — the value to put into the location filter. */
    val location: String = "",
    val city: String? = null,
    val zip: String? = null,
    val countryCode: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    /** How many active listings sit in this place. */
    val count: Int = 0
) {
    fun display(): String = displayName ?: location
}

// ── Map pins (/api/search?mapMode=true) — trimmed payload, one entry per marker ──
data class MapPinsResponse(
    val listings: List<MapPin> = emptyList(),
    val total: Int = 0,
)

data class MapPin(
    @SerializedName("_id") val id: String = "",
    val title: String = "",
    val price: Double? = null,
    val priceType: String? = null,
    val category: String? = null,
    val location: String? = null,
    val image: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)
