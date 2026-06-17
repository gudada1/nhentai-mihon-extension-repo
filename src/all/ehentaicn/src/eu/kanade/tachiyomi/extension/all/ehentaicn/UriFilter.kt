package eu.kanade.tachiyomi.extension.all.ehentaicn

import android.net.Uri

/**
 * Uri filter
 */
interface UriFilter {
    fun addToUri(builder: Uri.Builder)
}
