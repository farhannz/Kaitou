package com.farhannz.kaitou.helpers

import android.content.Context
import android.content.Intent
import androidx.core.content.edit

class PermissionManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("media_projection_prefs", Context.MODE_PRIVATE)

    fun saveMediaProjectionPermission(resultCode: Int, data: Intent) {
        prefs.edit().apply {
            putInt("result_code", resultCode)
            putString("data_uri", data.toUri(Intent.URI_INTENT_SCHEME))
            apply()
        }
    }

    fun getMediaProjectionPermission(): Pair<Int, Intent>? {
        val resultCode = prefs.getInt("result_code", -1)
        val dataUri = prefs.getString("data_uri", null)

        return if (resultCode != -1 && dataUri != null) {
            try {
                Pair(resultCode, Intent.parseUri(dataUri, Intent.URI_INTENT_SCHEME))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearMediaProjectionPermission() {
        prefs.edit { clear() }
    }
}