package at.nimmdas.app.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.ui.screens.auth.LoginScreen
import at.nimmdas.app.ui.screens.auth.RegisterScreen
import at.nimmdas.app.ui.screens.create.CreateListingScreen
import at.nimmdas.app.ui.screens.home.HomeScreen
import at.nimmdas.app.ui.screens.listing.ListingDetailScreen
import at.nimmdas.app.ui.screens.coins.CoinCenterScreen
import at.nimmdas.app.ui.screens.messages.ChatScreen
import at.nimmdas.app.ui.screens.messages.MessagesScreen
import at.nimmdas.app.ui.screens.profile.ProfileScreen
import at.nimmdas.app.ui.screens.profile.EditProfileScreen
import at.nimmdas.app.ui.screens.profile.SavedSearchesScreen
import at.nimmdas.app.ui.screens.profile.StatsScreen
import at.nimmdas.app.ui.screens.profile.DraftsScreen
import at.nimmdas.app.ui.screens.search.SearchScreen
import at.nimmdas.app.BuildConfig
import at.nimmdas.app.ui.screens.info.WebPageScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Create : Screen("create")
    object Messages : Screen("messages")
    object Profile : Screen("profile")
    object Login : Screen("login")
    object Register : Screen("register")
    object ListingDetail : Screen("listing/{id}") {
        fun createRoute(id: String) = "listing/$id"
    }
    object EditListing : Screen("edit/{id}") {
        fun createRoute(id: String) = "edit/$id"
    }
    object Chat : Screen("chat/{threadId}") {
        fun createRoute(threadId: String) = "chat/$threadId"
    }
    object PublicProfile : Screen("public_profile/{userId}") {
        fun createRoute(userId: String) = "public_profile/$userId"
    }
    object CoinCenter : Screen("coins")
    object EditProfile : Screen("edit_profile")
    object SavedSearches : Screen("saved_searches")
    object Stats : Screen("stats")
    object Drafts : Screen("drafts")
    object Call : Screen("call/{partnerId}/{listingId}?partnerName={partnerName}&partnerAvatar={partnerAvatar}") {
        fun createRoute(partnerId: String, listingId: String?, partnerName: String, partnerAvatar: String?): String {
            val encName = java.net.URLEncoder.encode(partnerName, "UTF-8")
            val encAvatar = partnerAvatar?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            val safeListingId = if (listingId.isNullOrEmpty()) "null" else listingId
            return "call/$partnerId/$safeListingId?partnerName=$encName&partnerAvatar=$encAvatar"
        }
    }
    object IncomingCall : Screen("incoming_call/{callId}") {
        fun createRoute(callId: String, callerName: String, callerAvatar: String?, offerSdp: String, listingTitle: String?, autoAnswer: Boolean): String {
            // Store large data (SDP) in memory — URL encoding corrupts it
            at.nimmdas.app.webrtc.PendingCallData.set(callId, callerName, callerAvatar, offerSdp, listingTitle, autoAnswer)
            return "incoming_call/$callId"
        }
    }
    object WebPage : Screen("webpage?url={url}&title={title}") {
        fun createRoute(url: String, title: String): String {
            val encUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "webpage?url=$encUrl&title=$encTitle"
        }
    }
    /** In-app PDF / video viewer for listing attachments. */
    object DocumentViewer : Screen("document?url={url}&title={title}") {
        fun createRoute(url: String, title: String): String {
            val encUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val encTitle = java.net.URLEncoder.encode(title, "UTF-8")
            return "document?url=$encUrl&title=$encTitle"
        }
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, "Suche", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Create, "+", Icons.Filled.AddCircle, Icons.Outlined.AddCircle),
    BottomNavItem(Screen.Messages, "Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
    BottomNavItem(Screen.Profile, "Profil", Icons.Filled.Person, Icons.Outlined.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onGoogleToken: String?,
    initialRoute: String?,
    intentFlow: kotlinx.coroutines.flow.SharedFlow<android.content.Intent>? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as NimmdasApp
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(intentFlow) {
        intentFlow?.collect { intent ->
            val navigateTo = intent.getStringExtra("navigate_to")
            if (navigateTo == "incoming_call") {
                val callId = intent.getStringExtra("callId") ?: return@collect
                val callerName = intent.getStringExtra("callerName") ?: ""
                val callerAvatar = intent.getStringExtra("callerAvatar")
                val offerSdp = intent.getStringExtra("offerSdp") ?: ""
                val listingTitle = intent.getStringExtra("listingTitle")
                val autoAnswer = intent.getBooleanExtra("autoAnswer", false)
                val route = Screen.IncomingCall.createRoute(callId, callerName, callerAvatar, offerSdp, listingTitle, autoAnswer)
                
                while (navController.currentDestination == null) {
                    kotlinx.coroutines.delay(50)
                }
                navController.navigate(route)
            } else if (navigateTo == "chat") {
                val threadId = intent.getStringExtra(at.nimmdas.app.push.NotificationHelper.EXTRA_THREAD_ID)
                if (threadId != null) {
                    while (navController.currentDestination == null) {
                        kotlinx.coroutines.delay(50)
                    }
                    navController.navigate(Screen.Chat.createRoute(threadId))
                }
            }
        }
    }

    // Check login state on launch
    LaunchedEffect(Unit) {
        isLoggedIn = app.apiClient.isLoggedIn()
        startDestination = Screen.Home.route // Always use a base route for NavHost
        
        // If app was launched from a deep link/push, navigate to it safely
        if (initialRoute != null) {
            while (navController.currentDestination == null) {
                kotlinx.coroutines.delay(50)
            }
            navController.navigate(initialRoute)
        }
    }

    // Server rejected our token (expired/invalid) → drop the session and ask for a new login
    // instead of leaving the user on screens that silently stay empty.
    LaunchedEffect(Unit) {
        at.nimmdas.app.data.api.SessionEvents.expired.collect {
            if (isLoggedIn) {
                isLoggedIn = false
                android.widget.Toast.makeText(
                    context,
                    "Sitzung abgelaufen – bitte erneut anmelden",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                while (navController.currentDestination == null) {
                    kotlinx.coroutines.delay(50)
                }
                navController.navigate(Screen.Login.route) {
                    popUpTo(0)
                }
            }
            at.nimmdas.app.data.api.SessionEvents.reset()
        }
    }

    // Poll for incoming calls & register FCM
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            // Re-register the push token now that we are logged in. The F-Droid build
            // has no push service, so this is a no-op there.
            at.nimmdas.app.push.PushRegistrar.register(context)

            while (isActive) {
                try {
                    val response = app.apiClient.api.getIncomingCall()
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.incomingCall != null) {
                            val call = body.incomingCall
                            val callerName = call.callerId?.name ?: "Anonym"
                            val callerAvatar = call.callerId?.avatar
                            
                            // Prevent navigating multiple times to the same call
                            val currentRoute = navController.currentDestination?.route
                            if (currentRoute?.startsWith("incoming_call") != true && currentRoute?.startsWith("call") != true) {
                                navController.navigate(Screen.IncomingCall.createRoute(call.id, callerName, callerAvatar, call.offerSDP, null, false))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Nimmdas", "Incoming call poll error", e)
                }
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    // Handle Deep Link Google Login Token arriving (deep link)
    LaunchedEffect(onGoogleToken) {
        if (onGoogleToken != null) {
            isLoggedIn = true
            // Navigate to home if currently on login
            if (startDestination != null) {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    if (startDestination == null) return // Loading

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar only on detail/chat/login screens
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route, Screen.Search.route, Screen.Create.route,
        Screen.Messages.route, Screen.Profile.route, Screen.CoinCenter.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // Floating capsule bottom nav — matching website design
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 12.dp,
                        tonalElevation = 0.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val isSelected = navBackStackEntry?.destination?.hierarchy?.any {
                                    it.route == item.screen.route
                                } == true

                                val isCreate = item.screen == Screen.Create

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            val targetRoute = if (
                                                item.screen == Screen.Create ||
                                                item.screen == Screen.Messages ||
                                                item.screen == Screen.Profile
                                            ) {
                                                if (!isLoggedIn) Screen.Login.route else item.screen.route
                                            } else {
                                                item.screen.route
                                            }
                                            navController.navigate(targetRoute) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = false
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                ) {
                                    if (isCreate) {
                                        // Elevated FAB-style create button
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            shadowElevation = 8.dp,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .offset(y = (-8).dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Filled.Add,
                                                    contentDescription = "Erstellen",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                        else Color.Transparent
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                )
                                            }
                                            Text(
                                                item.label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onSearchClick = { navController.navigate(Screen.Search.route) }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Create.route) {
                CreateListingScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.navigate(Screen.Home.route) { popUpTo(0) } }
                )
            }
            composable(Screen.Messages.route) {
                MessagesScreen(
                    onThreadClick = { threadId -> navController.navigate(Screen.Chat.createRoute(threadId)) }
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        scope.launch {
                            app.apiClient.logout()
                            app.watchlist.clear()
                            isLoggedIn = false
                            navController.navigate(Screen.Home.route) { popUpTo(0) }
                        }
                    },
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onCoinsClick = { navController.navigate(Screen.CoinCenter.route) },
                    onEditProfile = { navController.navigate(Screen.EditProfile.route) },
                    onEditListing = { id -> navController.navigate(Screen.EditListing.createRoute(id)) },
                    onSavedSearchesClick = { navController.navigate(Screen.SavedSearches.route) },
                    onStatsClick = { navController.navigate(Screen.Stats.route) },
                    onDraftsClick = { navController.navigate(Screen.Drafts.route) },
                    onWebPageClick = { url, title ->
                        navController.navigate(Screen.WebPage.createRoute(url, title))
                    }
                )
            }
            composable(Screen.CoinCenter.route) {
                CoinCenterScreen(
                    onBack = { navController.popBackStack() },
                    onWebPageClick = { url, title ->
                        navController.navigate(Screen.WebPage.createRoute(url, title))
                    }
                )
            }
            composable(Screen.EditProfile.route) {
                EditProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.SavedSearches.route) {
                SavedSearchesScreen(
                    onBack = { navController.popBackStack() },
                    // Agents are created from the search screen, with the current filters.
                    onCreateNew = { navController.navigate(Screen.Search.route) },
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    onBack = { navController.popBackStack() },
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onUnlockClick = { navController.navigate(Screen.CoinCenter.route) },
                )
            }
            composable(Screen.Drafts.route) {
                DraftsScreen(
                    onBack = { navController.popBackStack() },
                    onEditDraft = { id -> navController.navigate(Screen.EditListing.createRoute(id)) }
                )
            }
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    },
                    onGoToRegister = { navController.navigate(Screen.Register.route) },
                    onForgotPasswordClick = {
                        navController.navigate(Screen.WebPage.createRoute("${BuildConfig.API_BASE_URL}/passwort-vergessen", "Passwort vergessen"))
                    }
                )
            }
            composable(Screen.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Home.route) { popUpTo(0) }
                    },
                    onGoToLogin = { navController.popBackStack() }
                )
            }
            composable(
                Screen.ListingDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val lid = backStackEntry.arguments?.getString("id") ?: ""
                ListingDetailScreen(
                    listingId = lid,
                    onBack = { navController.popBackStack() },
                    onChat = { sellerId -> navController.navigate(Screen.Chat.createRoute("${sellerId}_${lid}")) },
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onSellerClick = { userId -> navController.navigate(Screen.PublicProfile.createRoute(userId)) },
                    onEditClick = { id -> navController.navigate(Screen.EditListing.createRoute(id)) },
                    onShadowmap = { url, title -> navController.navigate(Screen.WebPage.createRoute(url, title)) },
                    onOpenDocument = { url, title -> navController.navigate(Screen.DocumentViewer.createRoute(url, title)) }
                )
            }
            composable(
                Screen.EditListing.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                val lid = backStackEntry.arguments?.getString("id") ?: ""
                CreateListingScreen(
                    editListingId = lid,
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() }
                )
            }
            composable(
                Screen.Chat.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                ChatScreen(
                    threadId = threadId,
                    onBack = { navController.popBackStack() },
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) },
                    onCallRequested = { partnerId, partnerName, partnerAvatar, listingId ->
                        navController.navigate(Screen.Call.createRoute(partnerId, listingId, partnerName, partnerAvatar))
                    },
                    onOpenDocument = { url, title ->
                        navController.navigate(Screen.DocumentViewer.createRoute(url, title))
                    },
                )
            }
            composable(
                route = Screen.Call.route,
                arguments = listOf(
                    navArgument("partnerId") { type = NavType.StringType },
                    navArgument("listingId") { type = NavType.StringType },
                    navArgument("partnerName") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("partnerAvatar") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val partnerId = backStackEntry.arguments?.getString("partnerId") ?: ""
                val rawListingId = backStackEntry.arguments?.getString("listingId")
                val listingId = if (rawListingId == "null") null else rawListingId
                val rawPartnerName = backStackEntry.arguments?.getString("partnerName") ?: "Anonym"
                val partnerName = java.net.URLDecoder.decode(rawPartnerName, "UTF-8")
                val rawAvatar = backStackEntry.arguments?.getString("partnerAvatar")
                val partnerAvatar = rawAvatar?.takeIf { it.isNotBlank() }?.let { java.net.URLDecoder.decode(it, "UTF-8") }

                at.nimmdas.app.ui.screens.messages.CallScreen(
                    partnerId = partnerId,
                    partnerName = partnerName,
                    partnerAvatar = partnerAvatar,
                    listingId = listingId,
                    onCallEnded = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.IncomingCall.route,
                arguments = listOf(
                    navArgument("callId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val callId = backStackEntry.arguments?.getString("callId") ?: ""
                // Read SDP and metadata from in-memory holder (not URL args)
                val pending = at.nimmdas.app.webrtc.PendingCallData
                val callerName = pending.callerName ?: "Anonym"
                val callerAvatar = pending.callerAvatar
                val offerSdp = pending.offerSdp ?: ""
                val listingTitle = pending.listingTitle
                val autoAnswer = pending.autoAnswer

                at.nimmdas.app.ui.screens.messages.IncomingCallScreen(
                    callId = callId,
                    callerName = callerName,
                    callerAvatar = callerAvatar,
                    offerSdp = offerSdp,
                    listingTitle = listingTitle,
                    autoAnswer = autoAnswer,
                    onCallEnded = {
                        pending.clear()
                        navController.popBackStack()
                    }
                )
            }
            composable(
                Screen.PublicProfile.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                at.nimmdas.app.ui.screens.profile.PublicProfileScreen(
                    userId = backStackEntry.arguments?.getString("userId") ?: "",
                    onBack = { navController.popBackStack() },
                    onListingClick = { id -> navController.navigate(Screen.ListingDetail.createRoute(id)) }
                )
            }
            composable(
                route = Screen.WebPage.route,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val rawUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(rawUrl, "UTF-8")
                val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
                val title = java.net.URLDecoder.decode(rawTitle, "UTF-8")

                WebPageScreen(
                    url = url,
                    title = title,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.DocumentViewer.route,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val rawUrl = backStackEntry.arguments?.getString("url") ?: ""
                val rawTitle = backStackEntry.arguments?.getString("title") ?: ""
                at.nimmdas.app.ui.screens.info.DocumentViewerScreen(
                    url = java.net.URLDecoder.decode(rawUrl, "UTF-8"),
                    title = java.net.URLDecoder.decode(rawTitle, "UTF-8"),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
