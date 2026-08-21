package dev.vibex.companion.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "vibex.device.credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun put(hostId: String, token: String) {
        prefs.edit().putString(hostId, token).apply()
    }

    fun get(hostId: String): String? = prefs.getString(hostId, null)

    fun remove(hostId: String) {
        prefs.edit().remove(hostId).apply()
    }
}
