package com.eyalm.adns.data.controld

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Guarda el token de API de ControlD cifrado.
 * Usa archivos y clave propios, separados de los de NextDNS ([com.eyalm.adns.data.TokenManager]).
 */
class ControlDTokenStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("controld_secure_prefs", Context.MODE_PRIVATE)

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, "controld_tink_keyset", "controld_tink_prefs_internal")
            .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri("android-keystore://controld_master_key")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    @Synchronized
    fun saveToken(token: String) {
        val encrypted = aead.encrypt(token.toByteArray(Charsets.UTF_8), null)
        prefs.edit { putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.DEFAULT)) }
    }

    @Synchronized
    fun getToken(): String? {
        val encoded = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            String(aead.decrypt(Base64.decode(encoded, Base64.DEFAULT), null), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun hasToken(): Boolean = getToken() != null

    @Synchronized
    fun saveEmail(email: String?) {
        prefs.edit { if (email.isNullOrBlank()) remove(KEY_EMAIL) else putString(KEY_EMAIL, email) }
    }

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    @Synchronized
    fun clear() {
        prefs.edit { remove(KEY_TOKEN); remove(KEY_EMAIL) }
    }

    companion object {
        private const val KEY_TOKEN = "api_token"
        private const val KEY_EMAIL = "email"

        @Volatile
        private var instance: ControlDTokenStore? = null

        fun getInstance(context: Context): ControlDTokenStore =
            instance ?: synchronized(this) {
                instance ?: ControlDTokenStore(context).also { instance = it }
            }
    }
}
