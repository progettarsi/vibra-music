/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import moe.rukamori.archivetune.ui.utils.backToMain
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.*
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.component.*
import moe.rukamori.archivetune.utils.ProxyUtils
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Proxy
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdvancedSettings(navController: NavController) {
        val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val (dnsOverHttpsEnabled, onDnsOverHttpsEnabledChange) = rememberPreference(key = EnableDnsOverHttpsKey, defaultValue = false)
    val (dnsProvider, onDnsProviderChange) = rememberPreference(key = DnsOverHttpsProviderKey, defaultValue = "Cloudflare")
    val (customDnsUrl, onCustomDnsUrlChange) = rememberPreference(key = stringPreferencesKey("customDnsUrl"), defaultValue = "https://")
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyHost, onProxyHostChange) = rememberPreference(key = ProxyHostKey, defaultValue = "")
    val (proxyPort, onProxyPortChange) = rememberPreference(key = ProxyPortKey, defaultValue = 8080)
    val (proxyUsername, onProxyUsernameChange) = rememberPreference(key = ProxyUsernameKey, defaultValue = "")
    val (proxyPassword, onProxyPasswordChange) = rememberPreference(key = ProxyPasswordKey, defaultValue = "")
    val (streamBypassProxy, onStreamBypassProxyChange) = rememberPreference(key = StreamBypassProxyKey, defaultValue = false)

    val (reorderWobble, onReorderWobbleChange) = rememberPreference(app.vibra.ui.motion.ReorderWobbleKey, true)

    var testingProxy by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val dnsProviders = remember { listOf("Cloudflare", "Google", "AdGuard", "Quad9", "Custom") }
    val proxyTypes = remember { listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS) }
    Scaffold(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            moe.rukamori.archivetune.ui.component.EnginePageTopBar(title = stringResource(R.string.settings_advanced), navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                })
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.settings_advanced)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.reorder_wobble)) },
                        checked = reorderWobble,
                        onCheckedChange = onReorderWobbleChange,
                    )
                }
                item {
                    PreferenceEntry(title = { Text(stringResource(R.string.advanced_login)) },
                        onClick = { navController.navigate("settings/advanced/account") })
                }

                item {
                    PreferenceEntry(title = { Text(stringResource(R.string.settings_playback_audio)) },
                        onClick = { navController.navigate("settings/advanced/playback") })
                }

                item {
                    PreferenceEntry(title = { Text(stringResource(R.string.settings_advanced_content)) },
                        onClick = { navController.navigate("settings/advanced/content") })
                }
                item {
                }
                item {
                    PreferenceEntry(title = { Text(stringResource(R.string.debug_logs)) },
                        onClick = { navController.navigate("settings/logcat") })
                }
            }

            PreferenceGroup(title = stringResource(R.string.settings_integrations)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.settings_integrations)) },
                        onClick = { navController.navigate("settings/integrations") },
                    )
                }
            }
            PreferenceGroup(title = stringResource(R.string.dns_over_https)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.dns_over_https)) },
                        description = stringResource(R.string.dns_over_https_desc),
                        icon = { Icon(painterResource(R.drawable.security), null) },
                        checked = dnsOverHttpsEnabled,
                        onCheckedChange = onDnsOverHttpsEnabledChange,
                    )
                }

                item(visible = dnsOverHttpsEnabled) {
                    ListPreference(
                        title = { Text(stringResource(R.string.dns_provider)) },
                        icon = { Icon(painterResource(R.drawable.website), null) },
                        selectedValue = dnsProvider,
                        values = dnsProviders,
                        valueText = { it },
                        onValueSelected = onDnsProviderChange,
                    )
                }

                item(visible = dnsOverHttpsEnabled && dnsProvider == "Custom") {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.dns_custom_url)) },
                        value = customDnsUrl,
                        onValueChange = onCustomDnsUrlChange,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.proxy)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_proxy)) },
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        checked = proxyEnabled,
                        onCheckedChange = {
                            onProxyEnabledChange(it)
                            ProxyUtils.applyYouTubeProxy(it, proxyType, proxyHost, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    ListPreference(
                        title = { Text(stringResource(R.string.proxy_type)) },
                        selectedValue = proxyType,
                        values = proxyTypes,
                        valueText = { it.name },
                        onValueSelected = {
                            onProxyTypeChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, it, proxyHost, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.proxy_host)) },
                        value = proxyHost,
                        onValueChange = {
                            onProxyHostChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, it, proxyPort, proxyUsername, proxyPassword)
                        },
                    )
                }

                item(visible = proxyEnabled) {
                    NumberEditTextPreference(
                        title = { Text(stringResource(R.string.proxy_port)) },
                        value = proxyPort,
                        onValueChange = {
                            onProxyPortChange(it)
                            ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, it, proxyUsername, proxyPassword)
                        },
                        isInputValid = { it.toIntOrNull() in 1..65535 },
                    )
                }
            }

            if (proxyEnabled) {
                PreferenceGroup(title = stringResource(R.string.proxy_auth)) {
                    item {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_username)) },
                            value = proxyUsername,
                            onValueChange = {
                                onProxyUsernameChange(it)
                                ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, proxyPort, it, proxyPassword)
                            },
                        )
                    }

                    item {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.proxy_password)) },
                            value = proxyPassword,
                            onValueChange = {
                                onProxyPasswordChange(it)
                                ProxyUtils.applyYouTubeProxy(proxyEnabled, proxyType, proxyHost, proxyPort, proxyUsername, it)
                            },
                        )
                    }

                    item {
                        SwitchPreference(
                            title = { Text(stringResource(R.string.stream_bypass_proxy)) },
                            description = stringResource(R.string.stream_bypass_proxy_desc),
                            icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                            checked = streamBypassProxy,
                            onCheckedChange = {
                                onStreamBypassProxyChange(it)
                                YouTube.streamBypassProxy = it
                            },
                        )
                    }

                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.test_proxy_connection)) },
                            icon = { Icon(painterResource(R.drawable.check), null) },
                            onClick = {
                                if (testingProxy) return@PreferenceEntry
                                scope.launch(Dispatchers.IO) {
                                    testingProxy = true
                                    try {
                                        val proxy = ProxyUtils.createProxyOrNull(proxyType, proxyHost, proxyPort)
                                        if (proxy == null) {
                                            testResult =
                                                resources.getString(
                                                    R.string.proxy_connection_failed,
                                                    resources.getString(R.string.proxy_connection_invalid_configuration),
                                                )
                                            return@launch
                                        }
                                        val clientBuilder =
                                            OkHttpClient
                                                .Builder()
                                                .proxy(proxy)
                                                .connectTimeout(10, TimeUnit.SECONDS)
                                                .readTimeout(10, TimeUnit.SECONDS)

                                        if (proxyUsername.isNotBlank() && proxyPassword.isNotBlank()) {
                                            clientBuilder.proxyAuthenticator { _, response ->
                                                val credential = okhttp3.Credentials.basic(proxyUsername, proxyPassword)
                                                response.request
                                                    .newBuilder()
                                                    .header("Proxy-Authorization", credential)
                                                    .build()
                                            }
                                        }

                                        val client = clientBuilder.build()
                                        val request =
                                            Request
                                                .Builder()
                                                .url("https://music.youtube.com/generate_204")
                                                .build()
                                        client.newCall(request).execute().use { response ->
                                            testResult =
                                                if (response.isSuccessful || response.code == 204) {
                                                    resources.getString(R.string.proxy_connection_success)
                                                } else {
                                                    resources.getString(R.string.proxy_connection_failed, "HTTP ${response.code}")
                                                }
                                        }
                                    } catch (e: Exception) {
                                        testResult =
                                            resources.getString(
                                                R.string.proxy_connection_failed,
                                                e.message ?: resources.getString(R.string.error_unknown),
                                            )
                                    } finally {
                                        testingProxy = false
                                    }
                                }
                            },
                        )
                    }
                }
            }

        }
    }

    if (testingProxy) {
        DefaultDialog(
            onDismiss = { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.testing_proxy_connection))
        }
    }

    if (testResult != null) {
        ActionPromptDialog(
            title = stringResource(R.string.test_proxy_connection),
            onDismiss = { testResult = null },
            onConfirm = { testResult = null },
            content = {
                Text(testResult!!)
            },
        )
    }
}
