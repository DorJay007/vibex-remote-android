package dev.vibex.companion.app.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dev.vibex.companion.app.data.VibeXAccountRepository

/** Opens a page in an in-app Custom Tab; launched from the activity so it stays in the app's task. */
fun Context.openInAppBrowser(url: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url)) }
}

fun Context.openSitePage(path: String) = openInAppBrowser("${VibeXAccountRepository.SITE_BASE_URL}$path")
