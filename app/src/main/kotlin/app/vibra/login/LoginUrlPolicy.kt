/* Vibra (2026) — GPL-3.0 License. Derived from ArchiveTune © Rukamori (github.com/rukamori). */
package app.vibra.login

private val HTTPS_HOST = Regex("""^https://([a-z0-9.-]+)(?::443)?(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)

/** google.com, its two-part country forms (google.co.uk, google.com.br) and google.<cc>, with subdomains. */
private val GOOGLE_HOST = Regex("""^(?:[a-z0-9-]+\.)*google\.(?:com|(?:co\.|com\.)?[a-z]{2})$""")
private val YOUTUBE_HOST = Regex("""^(?:[a-z0-9-]+\.)*youtube\.com$""")

private val GOOGLE_ACCOUNTS_HOST = Regex("""^accounts\.google\.(?:com|(?:co\.|com\.)?[a-z]{2})$""")

/** Google services where anyone can publish a page: being on Google proves nothing there. */
private val GOOGLE_USER_CONTENT = setOf("sites", "docs", "drive", "forms", "script", "groups", "translate", "colab", "earth", "photos")

private fun hostOf(url: String): String? = HTTPS_HOST.find(url)?.groupValues?.get(1)?.lowercase()

/**
 * The only pages the login screen may show: HTTPS on YouTube or Google's own pages. The host is read
 * strictly, because browsers and URL parsers disagree on forms like `user@host` or a backslash in the
 * authority; anything but a plain host name is refused.
 */
fun isTrustedLoginUrl(url: String): Boolean {
    val host = hostOf(url) ?: return false
    if (YOUTUBE_HOST.matches(host)) return true
    return GOOGLE_HOST.matches(host) && host.split('.').none { it in GOOGLE_USER_CONTENT }
}

/** Where the login screen may open when asked from outside: only YouTube and the Google sign-in page. */
fun isTrustedLoginStartUrl(url: String): Boolean {
    val host = hostOf(url) ?: return false
    return YOUTUBE_HOST.matches(host) || GOOGLE_ACCOUNTS_HOST.matches(host)
}
