package com.eagleslab.yingge.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small Android-Keystore envelope used for offline drafts and pending write keys. */
internal class AndroidSecureStore(
    context: Context,
    name: String,
    private val alias: String,
) {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Synchronized
    fun put(key: String, value: String) {
        val storageKey = storageKey(key)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("${storageKey}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${storageKey}_payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun get(key: String): String? {
        val storageKey = storageKey(key)
        val iv = preferences.getString("${storageKey}_iv", null) ?: return null
        val payload = preferences.getString("${storageKey}_payload", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrElse { remove(key); null }
    }

    fun remove(key: String) {
        val storageKey = storageKey(key)
        preferences.edit().remove("${storageKey}_iv").remove("${storageKey}_payload").apply()
    }

    fun clear() = preferences.edit().clear().apply()

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun storageKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object { const val TRANSFORMATION = "AES/GCM/NoPadding" }
}
