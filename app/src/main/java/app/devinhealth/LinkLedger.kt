package app.devinhealth

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Remembers which links have been logged (last 48 h) so that Android re-delivering the
 * launch intent (reinstall, recents, process restart) cannot log the same reading twice.
 */
object LinkLedger {
    private const val PREFS = "logged_links"
    private const val KEEP_MS = 48L * 60 * 60 * 1000

    fun loggedAt(context: Context, uri: Uri): String? {
        prune(context)
        val millis = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(uri.toString(), -1L)
        if (millis < 0) return null
        return DateTimeFormatter.ofPattern("HH:mm, EEE d MMM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
    }

    fun markLogged(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(uri.toString(), System.currentTimeMillis()).apply()
    }

    private fun prune(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cutoff = System.currentTimeMillis() - KEEP_MS
        val stale = prefs.all.filter { (it.value as? Long ?: 0L) < cutoff }.keys
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }
}
