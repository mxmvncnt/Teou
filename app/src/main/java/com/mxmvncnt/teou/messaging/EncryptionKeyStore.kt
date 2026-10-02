package com.mxmvncnt.teou.messaging

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local receiving keys, distinct from the signing identity and UnifiedPush's connector keys. */
object EncryptionKeyStore {
    private const val ALIAS = "teou_push_encryption_v1"
    private const val PREFS = "teou_encryption_keys"
    private const val KEY = "encrypted_keys"

    data class Keys(val privateKey: ECPrivateKey, val publicKey: ByteArray, val auth: ByteArray) {
        val p256dh: String get() = WebPushCrypto.b64enc(publicKey)
        val authSecret: String get() = WebPushCrypto.b64enc(auth)
    }

    @Synchronized
    fun getOrCreate(context: Context): Keys {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (stored != null) {
            // Fail closed if storage or the wrapping key is lost; never silently rotate a paired key.
            val key = keystore.getKey(ALIAS, null) as SecretKey
            val sealed = WebPushCrypto.b64dec(stored)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(sealed.copyOfRange(12, sealed.size)), Charsets.UTF_8))
            return Keys(
                KeyFactory.getInstance("EC").generatePrivate(
                    PKCS8EncodedKeySpec(WebPushCrypto.b64dec(json.getString("private")))
                ) as ECPrivateKey,
                WebPushCrypto.b64dec(json.getString("public")),
                WebPushCrypto.b64dec(json.getString("auth"))
            )
        }
        val wrappingKey = if (keystore.containsAlias(ALIAS)) {
            keystore.getKey(ALIAS, null) as SecretKey
        } else {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
        val pair = WebPushCrypto.generateKeyPair()
        val keys = Keys(pair.private as ECPrivateKey, WebPushCrypto.pointBytes(pair.public as ECPublicKey),
            ByteArray(16).also { SecureRandom().nextBytes(it) })
        val json = JSONObject().put("private", WebPushCrypto.b64enc(pair.private.encoded))
            .put("public", keys.p256dh).put("auth", keys.authSecret)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey)
        val sealed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(KEY, WebPushCrypto.b64enc(sealed)).commit()) { "Could not persist receiving keys" }
        return keys
    }
}
