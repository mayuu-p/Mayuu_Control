package com.mayuu.phantomrecover

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DeviceSecrets {
    private const val PREFS = "phantom_secure_prefs"
    private const val KEY_ALIAS = "phantom_session_aes_gcm_v1"
    private const val SESSION_CIPHER = "AES/GCM/NoPadding"

    fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString("install_id", null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("install_id", created).apply()
        return created
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun saveSession(context: Context, secret: String, deviceId: String) {
        require(secret.length >= 32)
        val cipher = Cipher.getInstance(SESSION_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("session_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("session_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("device_id", deviceId).apply()
    }

    fun loadSession(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivText = prefs.getString("session_iv", null) ?: return null
        val cipherText = prefs.getString("session_ciphertext", null) ?: return null
        return try {
            val cipher = Cipher.getInstance(SESSION_CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clearSession(context); null
        }
    }

    fun deviceId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("device_id", null)

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("session_iv").remove("session_ciphertext").remove("device_id").apply()
    }
}
