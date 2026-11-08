/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUrlPolicyTest {
    @Test fun acceptsTheLoginFlow() {
        listOf(
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com",
            "https://music.youtube.com/",
            "https://www.youtube.com/watch?v=abc|def",
            "https://youtube.com",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://consent.youtube.com/m?continue=x",
            "https://accounts.google.it/accounts/SetSID",
            "https://accounts.google.co.uk/x",
            "https://www.google.com.br/x",
            "HTTPS://Accounts.Google.COM:443/x",
        ).forEach { assertTrue(it, isTrustedLoginUrl(it)) }
    }

    @Test fun refusesOtherSitesAndLookalikes() {
        listOf(
            "https://evil.example/login",
            "https://youtube.com.evil.example/",
            "https://google.evil.com/",
            "https://notyoutube.com/",
            "https://notgoogle.com/",
            "https://google.xyz/",
            "https://google.com.evil.io/",
        ).forEach { assertFalse(it, isTrustedLoginUrl(it)) }
    }

    @Test fun refusesGooglePagesAnyoneCanPublish() {
        listOf(
            "https://sites.google.com/view/fake-login",
            "https://docs.google.com/forms/d/1/viewform",
            "https://script.google.com/macros/s/1/exec",
            "https://translate.google.com/translate?u=https://evil.example",
            "https://sites.google.it/view/x",
        ).forEach { assertFalse(it, isTrustedLoginUrl(it)) }
    }

    @Test fun startUrlIsOnlyYouTubeOrTheSignInPage() {
        listOf(
            "https://music.youtube.com/watch?v=abc",
            "https://accounts.google.com/ServiceLogin",
            "https://accounts.google.it/x",
        ).forEach { assertTrue(it, isTrustedLoginStartUrl(it)) }
        listOf(
            "https://www.google.com/url?q=https://evil.example",
            "https://support.google.com/accounts",
            "https://sites.google.com/view/x",
            "https://evil.example/",
            "http://music.youtube.com/",
        ).forEach { assertFalse(it, isTrustedLoginStartUrl(it)) }
    }

    @Test fun refusesOtherSchemes() {
        listOf(
            "http://accounts.google.com/",
            "javascript:alert(1)",
            "data:text/html,<script>1</script>",
            "blob:https://accounts.google.com/1",
            "file:///data/data/x",
            "intent://accounts.google.com/#Intent;scheme=https;end",
            "",
        ).forEach { assertFalse(it, isTrustedLoginUrl(it)) }
    }

    @Test fun refusesAuthorityTricks() {
        listOf(
            "https://youtube.com@evil.example/",
            "https://evil.example\\.youtube.com/",
            "https://evil.example\t.youtube.com/",
            "https://youtube.com:8443/",
            " https://youtube.com/",
        ).forEach { assertFalse(it, isTrustedLoginUrl(it)) }
    }
}
