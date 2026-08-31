package com.farhannz.kaitou

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.farhannz.kaitou.helpers.Logger

/**
 * Persists the MediaProjection consent (resultCode + result data Intent).
 *
 * The system offers no API to query whether a projection consent is still
 * valid, and on Android 14+ the consent is invalidated on every reboot.
 * So validity is tracked app-side: the consent is stored together with the
 * boot count at grant time, and treated as stale once the device reboots.
 */
object MediaProjectionPermissionStore {
    private const val PREFS_FILE = "capture_consent"
    private const val KEY_RESULT_CODE = "resultCode"
    private const val KEY_DATA = "data"
    private const val KEY_BOOT_COUNT = "bootCount"

    private val logger = Logger(MediaProjectionPermissionStore::class.simpleName!!)

    fun save(context: Context, resultCode: Int, data: Intent) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RESULT_CODE, resultCode)
            .putString(KEY_DATA, data.toUri(0))
            .putInt(KEY_BOOT_COUNT, currentBootCount(context))
            .apply()
    }

    /**
     * Returns the stored (resultCode, dataIntent) pair if a consent is present
     * and has not been invalidated by a reboot, null otherwise.
     */
    fun load(context: Context): Pair<Int, Intent>? {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_RESULT_CODE) || !prefs.contains(KEY_DATA)) return null
        if (prefs.getInt(KEY_BOOT_COUNT, -1) != currentBootCount(context)) {
            logger.DEBUG("Capture consent invalidated by reboot")
            return null
        }
        return try {
            val data = Intent.parseUri(prefs.getString(KEY_DATA, null), 0)
            prefs.getInt(KEY_RESULT_CODE, Int.MIN_VALUE) to data
        } catch (e: Exception) {
            logger.ERROR("Failed to unmarshal capture consent: ${e.message}")
            clear(context)
            null
        }
    }

    /**
     * Best-effort validity check. Note: even when this returns true, Android 14+
     * may reject the token if the projection was stopped earlier in this boot
     * (single-use per foreground service start). Callers must handle the
     * SecurityException path by re-requesting consent.
     */
    fun isValid(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun currentBootCount(context: Context): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (e: Exception) {
        logger.WARNING("Failed to read BOOT_COUNT: ${e.message}")
        -1
    }
}
