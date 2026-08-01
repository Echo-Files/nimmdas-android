package at.nimmdas.app.data.api

import at.nimmdas.app.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface NimmdasApi {

    // ── Auth (moved outside /api/auth/ to avoid NextAuth catch-all)
    @POST("api/mobile/auth")
    suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/mobile/auth")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    // ── Mobile API (auth via interceptor) ──────────
    @GET("api/mobile/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("api/mobile/push/register")
    suspend fun registerPushToken(@Body body: Map<String, String>): Response<Map<String, Boolean>>

    // ── Voice Calling API ──────────
    @POST("api/mobile/calls")
    suspend fun createCall(@Body body: at.nimmdas.app.data.model.CreateCallRequest): Response<at.nimmdas.app.data.model.CreateCallResponse>

    @GET("api/mobile/calls")
    suspend fun getIncomingCall(): Response<at.nimmdas.app.data.model.IncomingCallResponse>

    @GET("api/mobile/calls/{id}")
    suspend fun getCallStatus(@Path("id") callId: String): Response<at.nimmdas.app.data.model.CallStatusResponse>

    @PATCH("api/mobile/calls/{id}")
    suspend fun updateCallStatus(@Path("id") callId: String, @Body body: at.nimmdas.app.data.model.UpdateCallRequest): Response<Map<String, Boolean>>

    @GET("api/mobile/calls/{id}/ice")
    suspend fun getIceCandidates(@Path("id") callId: String): Response<at.nimmdas.app.data.model.IceCandidatesResponse>

    @POST("api/mobile/calls/{id}/ice")
    suspend fun addIceCandidate(@Path("id") callId: String, @Body body: at.nimmdas.app.data.model.IceCandidateData): Response<Map<String, Boolean>>

    @GET("api/mobile/listings")
    suspend fun getMyListings(): Response<List<Listing>>

    // ── Public Listings (no auth) ────────────────
    @GET("api/listings")
    suspend fun getListings(
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<Listing>>

    @GET("api/listings/{id}")
    suspend fun getListingById(@Path("id") id: String): Response<Listing>

    @POST("api/mobile/listings/create")
    suspend fun createListing(
        @Body listing: CreateListingRequest
    ): Response<Listing>



    // ── NLP Smart Intent (no auth) ───────────────
    @GET("api/search/smart-intent")
    suspend fun smartIntent(@Query("q") query: String): Response<SmartIntentResponse>

    /** Guesses category, subcategory and brand/model from a listing title. */
    @GET("api/listings/predict-category")
    suspend fun predictCategory(
        @Query("title") title: String,
        @Query("description") description: String? = null,
    ): Response<CategoryPrediction>

    /**
     * Map pins for the current search — same filters, but a trimmed payload and a much
     * higher result cap so the map can show the whole result set at once.
     */
    @GET("api/search")
    suspend fun searchMapPins(
        @Query("mapMode") mapMode: String = "true",
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("condition") condition: String? = null,
        @Query("minPrice") minPrice: String? = null,
        @Query("maxPrice") maxPrice: String? = null,
        @Query("location") location: String? = null,
        @Query("radius") radius: String? = null,
        @Query("propertyType") propertyType: String? = null,
        @Query("roomsMin") roomsMin: String? = null,
        @Query("roomsMax") roomsMax: String? = null,
        @Query("sqmMin") sqmMin: String? = null,
        @Query("sqmMax") sqmMax: String? = null,
        @Query("brand") brand: String? = null,
        @Query("limit") limit: Int = 800,
    ): Response<MapPinsResponse>

    // ── Live search preview shown while typing (no auth) ──
    @GET("api/search/preview")
    suspend fun searchPreview(@Query("q") query: String): Response<SearchPreviewResponse>

    // ── "Auf gut Glück" — one random listing matching the context (no auth) ──
    @GET("api/search/lucky")
    suspend fun lucky(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("location") location: String? = null,
        @Query("brand") brand: String? = null,
    ): Response<LuckyResponse>

    // ── Location autocomplete for the location filter (no auth) ──
    @GET("api/locations/autocomplete")
    suspend fun locationAutocomplete(@Query("q") query: String): Response<List<LocationSuggestion>>

    // ── Search with ALL category-specific filters (no auth) ──
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("subcategory") subcategory: String? = null,
        @Query("condition") condition: String? = null,
        @Query("minPrice") minPrice: String? = null,
        @Query("maxPrice") maxPrice: String? = null,
        @Query("priceType") priceType: String? = null,
        @Query("location") location: String? = null,
        @Query("radius") radius: String? = null,
        @Query("shipping") shipping: String? = null,
        @Query("hasImages") hasImages: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        // ── Autos ──
        @Query("brand") brand: String? = null,
        @Query("model") model: String? = null,
        @Query("yearMin") yearMin: String? = null,
        @Query("yearMax") yearMax: String? = null,
        @Query("mileageMax") mileageMax: String? = null,
        @Query("fuelType") fuelType: String? = null,
        @Query("transmission") transmission: String? = null,
        @Query("powerMin") powerMin: String? = null,
        @Query("powerMax") powerMax: String? = null,
        @Query("color") color: String? = null,
        @Query("accidentFree") accidentFree: String? = null,
        // ── Immobilien ──
        @Query("propertyType") propertyType: String? = null,
        @Query("roomsMin") roomsMin: String? = null,
        @Query("roomsMax") roomsMax: String? = null,
        @Query("sqmMin") sqmMin: String? = null,
        @Query("sqmMax") sqmMax: String? = null,
        @Query("furnished") furnished: String? = null,
        @Query("balcony") balcony: String? = null,
        @Query("elevator") elevator: String? = null,
        @Query("parking") parking: String? = null,
        @Query("garden") garden: String? = null,
        @Query("cellar") cellar: String? = null,
        // ── Jobs ──
        @Query("jobType") jobType: String? = null,
        @Query("jobBranche") jobBranche: String? = null,
        @Query("salaryMin") salaryMin: String? = null,
        @Query("salaryMax") salaryMax: String? = null,
        @Query("experienceLevel") experienceLevel: String? = null,
        @Query("homeOffice") homeOffice: String? = null,
        // ── Dienstleistungen ──
        @Query("priceUnit") priceUnit: String? = null,
        @Query("serviceArea") serviceArea: String? = null,
        @Query("experience") experience: String? = null,
        @Query("availability") availability: String? = null,
        // ── Elektronik ──
        @Query("ram") ram: String? = null,
        @Query("storage") storage: String? = null,
        // ── Mode ──
        @Query("gender") gender: String? = null,
        @Query("clothingSize") clothingSize: String? = null,
        @Query("shoeSize") shoeSize: String? = null,
        @Query("material") material: String? = null,
        // ── Sport ──
        @Query("sportType") sportType: String? = null,
        @Query("frameSize") frameSize: String? = null,
        // ── Haustiere ──
        @Query("animalType") animalType: String? = null,
        @Query("breed") breed: String? = null,
        @Query("animalAge") animalAge: String? = null,
        @Query("animalGender") animalGender: String? = null,
        @Query("vaccinated") vaccinated: String? = null,
        @Query("neutered") neutered: String? = null,
        // ── Baby & Kind ──
        @Query("ageGroup") ageGroup: String? = null,
        // ── Musik ──
        @Query("instrumentType") instrumentType: String? = null,
        // ── Sammeln ──
        @Query("collectType") collectType: String? = null,
        @Query("rarity") rarity: String? = null,
        @Query("era") era: String? = null,
        // ── Möbel ──
        @Query("widthMax") widthMax: String? = null,
        @Query("heightMax") heightMax: String? = null,
        // ── Garten ──
        @Query("gartenType") gartenType: String? = null,
    ): Response<SearchResponse>

    // ── Messages (auth via interceptor) ───────────
    @GET("api/mobile/messages")
    suspend fun getThreads(): Response<List<MessageThread>>

    @GET("api/mobile/messages")
    suspend fun getThread(
        @Query("partnerId") partnerId: String,
        @Query("listingId") listingId: String
    ): Response<List<Message>>

    @POST("api/mobile/messages")
    suspend fun sendMessage(
        @Body message: SendMessageRequest
    ): Response<Message>

    /** Accepts or declines a price offer / coin redemption. */
    @POST("api/messages/offer-response")
    suspend fun respondToOffer(@Body body: OfferResponseRequest): Response<Map<String, Any>>

    /** Uploads a chat attachment (image or PDF). */
    @Multipart
    @POST("api/messages/upload")
    suspend fun uploadChatFile(@Part file: MultipartBody.Part): Response<ChatUploadResponse>

    // ── Upload ────────────────────────────────────
    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<Map<String, String>>

    // ── Coins (auth via interceptor) ──────────────
    @GET("api/mobile/coins")
    suspend fun getCoins(): Response<CoinData>

    @POST("api/mobile/coins")
    suspend fun coinAction(
        @Body action: CoinActionRequest
    ): Response<CoinActionResponse>

    // ── Watchlist (auth via interceptor) ──────────
    @GET("api/mobile/watchlist")
    suspend fun getWatchlist(): Response<WatchlistResponse>

    @POST("api/mobile/watchlist")
    suspend fun toggleWatchlist(@Body body: WatchlistRequest): Response<WatchlistToggleResponse>

    // ── Profile Edit (auth via interceptor) ──────
    @PUT("api/mobile/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): Response<MeResponse>

    // ── Listing Management (auth via interceptor) ─
    @PUT("api/mobile/listings/{id}")
    suspend fun updateListing(@Path("id") id: String, @Body body: CreateListingRequest): Response<Listing>

    @DELETE("api/mobile/listings/{id}")
    suspend fun deleteListing(@Path("id") id: String): Response<Map<String, Boolean>>

    @PUT("api/mobile/listings/{id}/status")
    suspend fun updateListingStatus(@Path("id") id: String, @Body body: StatusUpdateRequest): Response<Map<String, String>>

    @POST("api/mobile/listings/{id}/bump")
    suspend fun bumpListing(@Path("id") id: String): Response<Map<String, String>>

    // ── Public User Profile ──────────────────────
    @GET("api/mobile/users/{id}")
    suspend fun getUserProfile(@Path("id") id: String): Response<UserProfileResponse>

    // ── Reports ──────────────────────────────────
    @POST("api/mobile/reports")
    suspend fun reportListing(@Body body: ReportRequest): Response<Map<String, String>>

    // ── Upload ───────────────────────────────────
    @Multipart
    @POST("api/mobile/upload")
    suspend fun uploadImages(@Part images: List<okhttp3.MultipartBody.Part>): Response<UploadResponse>

    // ── Follow / Unfollow ────────────────────────
    @POST("api/mobile/users/{id}/follow")
    suspend fun followUser(@Path("id") id: String): Response<FollowResponse>

    @DELETE("api/mobile/users/{id}/follow")
    suspend fun unfollowUser(@Path("id") id: String): Response<FollowResponse>

    // ── Ratings ──────────────────────────────────
    @GET("api/mobile/ratings")
    suspend fun getRatings(@Query("userId") userId: String): Response<RatingsResponse>

    @POST("api/mobile/ratings")
    suspend fun createRating(@Body body: CreateRatingRequest): Response<Map<String, Any>>

    // ── Saved Searches (Suchagenten) ─────────────
    @GET("api/mobile/saved-searches")
    suspend fun getSavedSearches(): Response<List<SavedSearchItem>>

    @POST("api/mobile/saved-searches")
    suspend fun createSavedSearch(@Body body: CreateSavedSearchRequest): Response<SavedSearchItem>

    @DELETE("api/mobile/saved-searches")
    suspend fun deleteSavedSearch(@Query("id") id: String): Response<Map<String, Boolean>>

    // ── Avatar Upload ────────────────────────────
    @Multipart
    @POST("api/mobile/profile/avatar")
    suspend fun uploadAvatar(@Part avatar: okhttp3.MultipartBody.Part): Response<AvatarResponse>
}
