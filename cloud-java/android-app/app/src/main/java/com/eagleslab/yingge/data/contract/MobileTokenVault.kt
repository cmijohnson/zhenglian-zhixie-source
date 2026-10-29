package com.eagleslab.yingge.data.contract

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RefreshTokenRecord(val refreshToken: String, val refreshExpiresAt: String)

/** Access tokens are process-memory only; only the rotating refresh token is encrypted at rest. */
class MobileTokenVault(context: Context) {
    private val preferences = context.getSharedPreferences("yingge_mobile_tokens", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val accessTokensInMemory = ConcurrentHashMap<String, String>()

    @Synchronized
    fun save(origin: String, bundle: TokenBundle) {
        accessTokensInMemory[origin] = bundle.accessToken
        saveRefresh(origin, RefreshTokenRecord(bundle.refreshToken, bundle.refreshExpiresAt))
    }

    fun accessToken(origin: String): String? = accessTokensInMemory[origin]
    fun clearAccessToken(origin: String) { accessTokensInMemory.remove(origin) }
    fun clearAllAccessTokens() = accessTokensInMemory.clear()

    @Synchronized
    fun refreshToken(origin: String): RefreshTokenRecord? {
        val storageKey = TokenNamespace.key(origin)
        val iv = preferences.getString("${storageKey}_iv", null) ?: return null
        val payload = preferences.getString("${storageKey}_refresh", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
            gson.fromJson(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8), RefreshTokenRecord::class.java)
        }.getOrElse { clear(origin); null }
    }

    @Synchronized
    private fun saveRefresh(origin: String, record: RefreshTokenRecord) {
        val storageKey = TokenNamespace.key(origin)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(origin.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(gson.toJson(record).toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString("${storageKey}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${storageKey}_refresh", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .remove("iv").remove("refresh_payload").remove("payload")
            .apply()
    }

    fun clear(origin: String) {
        clearAccessToken(origin)
        val storageKey = TokenNamespace.key(origin)
        preferences.edit().remove("${storageKey}_iv").remove("${storageKey}_refresh").apply()
    }

    fun clearAll() { clearAllAccessTokens(); preferences.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    companion object {
        @Volatile private var instance: MobileTokenVault? = null
        fun get(context: Context): MobileTokenVault = instance ?: synchronized(this) {
            instance ?: MobileTokenVault(context.applicationContext).also { instance = it }
        }
        private const val ALIAS = "yingge_mobile_refresh_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

internal object TokenNamespace {
    fun key(origin: String): String = MessageDigest.getInstance("SHA-256")
        .digest(origin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
