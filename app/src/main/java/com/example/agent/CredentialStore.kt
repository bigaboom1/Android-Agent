// app/src/main/java/com/example/agent/CredentialStore.kt
package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object CredentialStore {
    private const val PREFS = "agent_prefs"
    private const val PREFS_FILE = "agent_credentials"
    private const val KEY_URL    = "server_url"
    private const val KEY_TOKEN  = "jwt_token"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_DEVICE_TOKEN = "device_token"


    private fun prefs(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Save server URL and JWT token to encrypted storage. */
    fun save(ctx: Context, url: String, token: String, deviceId: String) {
        prefs(ctx).edit()
            .putString(KEY_URL,   url)
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEVICE, deviceId)
            .apply()
    }

    /** Load saved credentials. Returns null if nothing saved yet.  */
    fun load(ctx: Context): Triple<String, String, String>? {
        val p        = prefs(ctx)
        val url      = p.getString(KEY_URL, null) ?: return null
        val token    = p.getString(KEY_TOKEN, null) ?: return null
        val deviceId = p.getString(KEY_DEVICE, null) ?: return null
        return Triple(url, token, deviceId)
    }

    /** Check if credentials exist without loading them. */
    fun has(ctx: Context): Boolean =
        prefs(ctx).contains(KEY_URL)

    /** Clear all saved credentials (on logout). */
    fun clear(ctx: Context) =
        prefs(ctx).edit().clear().apply()

    fun saveAi(context: Context, provider: String, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_provider", provider)
            .putString("ai_key", apiKey)
            .apply()
    }

    fun getAi(context: Context): AiConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val provider = prefs.getString("ai_provider", null)
        val key = prefs.getString("ai_key", null)
        if (provider != null && key != null) {
            return AiConfig(provider, key)
        }
        return null
    }
}