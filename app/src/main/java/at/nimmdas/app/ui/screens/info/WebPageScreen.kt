package at.nimmdas.app.ui.screens.info

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPageScreen(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NimmdasApp
    val scope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    
    var cookiesInitialized by remember { mutableStateOf(false) }
    var resolvedUrl by remember { mutableStateOf(url) }

    // Intercept back button to navigate WebView history if possible
    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    // Configure cookies (Language & Token Bridge) synchronously
    LaunchedEffect(url) {
        scope.launch {
            val token = app.apiClient.getToken()
            val locale = app.apiClient.getLocale()
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val baseDomain = BuildConfig.API_BASE_URL.removePrefix("https://").removePrefix("http://")

            // Setup language cookie
            cookieManager.setCookie(BuildConfig.API_BASE_URL, "nimmdas-locale=$locale; Domain=$baseDomain; Path=/; Secure; SameSite=Lax")
            
            // Setup authorization bridge cookie if logged in
            if (token != null) {
                cookieManager.setCookie(BuildConfig.API_BASE_URL, "nimmdas-token=$token; Domain=$baseDomain; Path=/; Secure; SameSite=Lax")
            }
            cookieManager.flush()

            // Seamless authentication bridge for our brand website
            if (token != null && url.startsWith(BuildConfig.API_BASE_URL)) {
                // Check if it's already a bridge URL to avoid infinite loops
                if (!url.contains("/api/auth/mobile-bridge")) {
                    // The token travels in the nimmdas-token cookie set above — keeping it
                    // out of the URL avoids leaking the JWT into logs and history.
                    val targetPath = url.removePrefix(BuildConfig.API_BASE_URL)
                    resolvedUrl = "${BuildConfig.API_BASE_URL}/api/auth/mobile-bridge?locale=$locale&redirect=$targetPath"
                }
            }
            
            cookiesInitialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webViewInstance?.canGoBack() == true) {
                            webViewInstance?.goBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sleek loading progress bar
            AnimatedVisibility(
                visible = isLoading,
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            if (cookiesInitialized) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                supportZoom()
                                builtInZoomControls = true
                                displayZoomControls = false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    progress = 0.1f
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    progress = 1.0f
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    // Keep navigation inside the WebView
                                    return false
                                }
                            }

                            // Load URL
                            loadUrl(resolvedUrl)
                        }
                    },
                    update = { webView ->
                        // Keep in sync if needed
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
