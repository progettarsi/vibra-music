/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.vibra.ui.components.VibraDialogButton
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.utils.hasCompleteYouTubeLoginCookies
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.resetAuthWebViewSession
import moe.rukamori.archivetune.viewmodels.LoginError
import moe.rukamori.archivetune.viewmodels.LoginScreenState
import moe.rukamori.archivetune.viewmodels.LoginViewModel

const val LOGIN_ROUTE = "login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val YOUTUBE_MUSIC_PACKAGE_NAME = "com.google.android.apps.youtube.music"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 10

private const val LOGIN_CONTEXT_SCRIPT =
    "(function(){try{var c=window.ytcfg;var y=window.yt&&window.yt.config_;var s=document.querySelectorAll('script');var v=c&&c.get&&c.get('VISITOR_DATA')||y&&y.VISITOR_DATA;var d=c&&c.get&&c.get('DATASYNC_ID')||y&&y.DATASYNC_ID;for(var i=0;i<s.length&&(!v||!d);i++){var x=s[i].textContent;if(!v){var vm=x.match(/[\"']VISITOR_DATA[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(vm)v=vm[1]}if(!d){var dm=x.match(/[\"'](?:DATASYNC_ID|dataSyncId)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(dm)d=dm[1]}}if(v)Android.onRetrieveVisitorData(v);if(d)Android.onRetrieveDataSyncId(d)}catch(e){}})();"

private val YOUTUBE_COOKIE_URLS =
    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    )

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
    onLoginComplete: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val loginSuccessMessage = stringResource(R.string.onboarding_profile_loaded)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var webViewCanGoBack by remember { mutableStateOf(false) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf(false) }
    var webViewGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(screenState, loginSuccessMessage) {
        if (screenState is LoginScreenState.Success) {
            // Vibra: keep the loaded profile visible briefly before completing onboarding.
            delay(1_000)
            if (onLoginComplete != null) {
                onLoginComplete()
            } else {
                navController.navigateUp()
            }
        }
    }

    Scaffold(topBar = {
    TopAppBar(
        title = { Text(stringResource(R.string.login)) },
        navigationIcon = {
            IconButton(
                onClick = {
                    if (onNavigateBack != null) {
                        onNavigateBack()
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (onNavigateBack != null) {
                        onNavigateBack()
                    } else {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
    )
    }) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            // A crashed renderer cannot be reused: a new generation recreates the WebView.
            key(webViewGeneration) {
            AndroidView(
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                        .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val cookieManager = CookieManager.getInstance()
                        webViewClient =
                            YouTubeLoginWebViewClient(
                                cookieManager = cookieManager,
                                onCookiesCaptured = viewModel::onCookiesCaptured,
                                onHistoryChanged = { webViewCanGoBack = it },
                                onPageLoadingChanged = { loading ->
                                    pageLoading = loading
                                    if (loading) pageError = false
                                },
                                onMainFrameError = { pageError = true },
                        onRenderProcessGone = {
                            pageError = true
                            webViewGeneration++
                        },
                            )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        val loginWebView = this
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onRetrieveVisitorData(newVisitorData: String?) {
                                    loginWebView.post {
                                        viewModel.onVisitorDataExtracted(newVisitorData)
                                    }
                                }

                                @JavascriptInterface
                                fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                    loginWebView.post {
                                        viewModel.onDataSyncIdExtracted(newDataSyncId)
                                    }
                                }

                            },
                            "Android",
                        )
                        webView = this
                        resetAuthWebViewSession(context, this, clearCookies = true) {
                            loadUrl(startUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGIN_URL)
                        }
                    }
                },
                onRelease = { releasedWebView ->
                    (releasedWebView.webViewClient as? YouTubeLoginWebViewClient)?.release(releasedWebView)
                    releasedWebView.removeJavascriptInterface("Android")
                    releasedWebView.stopLoading()
                    releasedWebView.destroy()
                    if (webView === releasedWebView) {
                        webView = null
                    }
                },
            )
            }
            val loadedProfile = (screenState as? LoginScreenState.Success)?.account
            if (loadedProfile != null) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(loginSuccessMessage, style = MaterialTheme.typography.headlineSmall)
                        if (loadedProfile.name.isNotBlank()) {
                            Text(loadedProfile.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (loadedProfile == null && (pageLoading || screenState is LoginScreenState.Loading)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    val loginError = (screenState as? LoginScreenState.Error)?.error
    if (pageError || loginError != null) {
        val dismiss = {
            pageError = false
            viewModel.dismissError()
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.login)) },
            text = { Text(stringResource(when {
                pageError -> R.string.login_page_error
                loginError == LoginError.MissingDataSyncId -> R.string.login_context_error
                else -> R.string.login_account_error
            })) },
            confirmButton = {
                VibraDialogButton(primary = true, onClick = {
                    pageError = false
                    if (loginError != null) viewModel.retryLogin() else webView?.reload()
                }) { Text(stringResource(R.string.retry)) }
            },
            dismissButton = {
                VibraDialogButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }




    BackHandler(enabled = onNavigateBack != null || webViewCanGoBack) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onNavigateBack?.invoke()
        }
    }
}

private class YouTubeLoginWebViewClient(
    private val cookieManager: CookieManager,
    private val onCookiesCaptured: (String) -> Unit,
    private val onHistoryChanged: (Boolean) -> Unit,
    private val onPageLoadingChanged: (Boolean) -> Unit,
    private val onMainFrameError: () -> Unit,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {
    private var extractionRunnable: Runnable? = null
    private var lastCapturedCookie: String? = null

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageLoadingChanged(true)
        onHistoryChanged(view.canGoBack())
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        // Handled: the screen shows the error and recreates the WebView instead of the
        // whole app being killed with the renderer.
        onRenderProcessGone()
        return true
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onPageLoadingChanged(false)
            onMainFrameError()
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val targetUrl = request.url.toString()
        if (!request.isForMainFrame && !targetUrl.isWebViewLoadableUrl()) return true
        return handleUrlLoading(view, targetUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean = handleUrlLoading(view, url)

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        onPageLoadingChanged(false)
        onHistoryChanged(view.canGoBack())
        scheduleLoginContextExtraction(view, url)
    }

    override fun doUpdateVisitedHistory(
        view: WebView,
        url: String?,
        isReload: Boolean,
    ) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view.canGoBack())
        scheduleLoginContextExtraction(view, url)
    }

    fun release(view: WebView) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null
    }

    private fun scheduleLoginContextExtraction(
        view: WebView,
        url: String?,
    ) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null

        if (!url.isYouTubeUrl()) return

        var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
        val runnable =
            object : Runnable {
                override fun run() {
                    if (!view.url.isYouTubeUrl()) return

                    view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
                    captureCookies(view.url)

                    remainingAttempts -= 1
                    if (remainingAttempts > 0) {
                        view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
                    }
                }
            }
        extractionRunnable = runnable
        view.post(runnable)
    }

    private fun captureCookies(currentUrl: String?) {
        val mergedCookie = mergeYouTubeCookies(cookieManager, currentUrl) ?: return
        if (!hasCompleteYouTubeLoginCookies(mergedCookie) || mergedCookie == lastCapturedCookie) return

        lastCapturedCookie = mergedCookie
        onCookiesCaptured(mergedCookie)
    }

    private fun handleUrlLoading(
        view: WebView,
        url: String?,
    ): Boolean {
        val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
        if (targetUrl.isYouTubeMusicStoreListingUrl()) return true
        if (targetUrl.isWebViewLoadableUrl()) return false

        targetUrl.intentWebViewUrl()?.let(view::loadUrl)
        return true
    }
}

private fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" ||
        scheme == "https" ||
        scheme == "javascript" ||
        scheme == "data" ||
        scheme == "blob"
}

private fun String.intentWebViewUrl(): String? {
    val parsedIntent =
        runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }.getOrNull()
    return sequenceOf(
        parsedIntent?.dataString,
        intentUriAsHttpsUrl(),
        parsedIntent
            ?.getStringExtra("browser_fallback_url")
            ?.takeUnless(String::isYouTubeMusicStoreListingUrl),
    ).firstOrNull { it?.isHttpUrl() == true }
}

private fun String.intentUriAsHttpsUrl(): String? {
    val intentUri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    if (!intentUri.scheme.equals("intent", ignoreCase = true)) return null

    val authority = intentUri.encodedAuthority ?: return null
    val path = intentUri.encodedPath.orEmpty()
    val query = intentUri.encodedQuery?.let { "?$it" }.orEmpty()
    return "https://$authority$path$query"
}

private fun String.isHttpUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}

private fun String.isYouTubeMusicStoreListingUrl(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    return uri.host.equals("play.google.com", ignoreCase = true) &&
        uri.path.equals("/store/apps/details", ignoreCase = true) &&
        uri.getQueryParameter("id") == YOUTUBE_MUSIC_PACKAGE_NAME
}

private fun String?.isYouTubeUrl(): Boolean {
    val host = this?.let(Uri::parse)?.host?.lowercase() ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}

private fun mergeYouTubeCookies(
    cookieManager: CookieManager,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()

    currentUrl.toYouTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(YOUTUBE_COOKIE_URLS)

    cookieManager.flush()

    candidateUrls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    cookieParts.putIfAbsent(key, value)
                }
            }
    }

    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

private fun String?.toYouTubeCookieOrigin(): String? {
    val parsed = this?.let(Uri::parse) ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null

    val scheme =
        parsed.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"

    return "$scheme://$host"
}
