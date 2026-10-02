package com.mxmvncnt.teou.messaging

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertThrows
import java.security.KeyPair
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Test

class WebPushCryptoTest {

    private val rfcAuth = "BTBZMqHH6r4Tts7J_aSIgg"
    private val rfcUaPublic = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
    private val rfcUaPrivate = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94"
    private val rfcAsPublic = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"
    private val rfcAsPrivate = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"
    private val rfcSalt = "DGv6ra1nlYgDCS1FRnbzlw"

    @Test
    fun deriveKeys_matchesRfc8291Vectors() {
        val asPrivate = WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcAsPrivate))
        val uaPublic = WebPushCrypto.b64dec(rfcUaPublic)
        val shared = WebPushCrypto.ecdh(asPrivate, WebPushCrypto.loadPublic(uaPublic))

        val keys = WebPushCrypto.deriveKeys(
            ecdhSecret = shared,
            auth = WebPushCrypto.b64dec(rfcAuth),
            uaPublic = uaPublic,
            asPublic = WebPushCrypto.b64dec(rfcAsPublic),
            salt = WebPushCrypto.b64dec(rfcSalt)
        )

        assertEquals("Snr3JMxaHVDXHWJn5wdC52WjpCtd2EIEGBykDcZW32k", WebPushCrypto.b64enc(keys.prkKey))
        assertEquals("S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg", WebPushCrypto.b64enc(keys.ikm))
        assertEquals("09_eUZGrsvxChDCGRCdkLiDXrReGOEVeSCdCcPBSJSc", WebPushCrypto.b64enc(keys.prk))
        assertEquals("oIhVW04MRdy2XN9CiKLxTg", WebPushCrypto.b64enc(keys.cek))
        assertEquals("4h_95klXJ5E_qnoN", WebPushCrypto.b64enc(keys.nonce))
    }

    @Test
    fun encrypt_matchesRfc8291Ciphertext() {
        val plaintext = "When I grow up, I want to be a watermelon".toByteArray(Charsets.UTF_8)
        val uaPublic = WebPushCrypto.b64dec(rfcUaPublic)
        val ephemeral = KeyPair(
            WebPushCrypto.loadPublic(WebPushCrypto.b64dec(rfcAsPublic)),
            WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcAsPrivate))
        )
        val salt = WebPushCrypto.b64dec(rfcSalt)

        val body = WebPushCrypto.encryptInternal(plaintext, uaPublic, WebPushCrypto.b64dec(rfcAuth), ephemeral, salt, 4096)

        assertArrayEquals(salt, body.copyOfRange(0, 16))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x10, 0x00), body.copyOfRange(16, 20))
        assertEquals(65, body[20].toInt() and 0xff)
        assertArrayEquals(WebPushCrypto.b64dec(rfcAsPublic), body.copyOfRange(21, 86))

        val ciphertext = body.copyOfRange(86, body.size)
        assertEquals(
            "8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ",
            WebPushCrypto.b64enc(ciphertext)
        )
    }

    @Test
    fun roundTrip_randomEphemeralAndSalt() {
        val receiver = WebPushCrypto.generateKeyPair()
        val receiverPublic = WebPushCrypto.pointBytes(receiver.public as ECPublicKey)
        val auth = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val message = "loc_request from peer".toByteArray(Charsets.UTF_8)

        val body = WebPushCrypto.encrypt(message, WebPushCrypto.b64enc(receiverPublic), WebPushCrypto.b64enc(auth))

        val decrypted = WebPushCrypto.decrypt(body, receiver.private as ECPrivateKey, receiverPublic, auth)
        assertArrayEquals(message, decrypted)
    }

    @Test
    fun loadPublic_acceptsOnCurvePoint() {
        val point = WebPushCrypto.b64dec(rfcUaPublic)

        val key = WebPushCrypto.loadPublic(point)

        assertArrayEquals(point, WebPushCrypto.pointBytes(key))
    }

    @Test
    fun loadPublic_rejectsOffCurvePoint() {
        val point = WebPushCrypto.b64dec(rfcUaPublic)
        val offCurve = point.copyOf()
        offCurve[64] = (offCurve[64].toInt() xor 0x01).toByte()

        try {
            WebPushCrypto.loadPublic(offCurve)
            fail("expected off-curve point to be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun decrypt_matchesRfc8291Vector() {
        val body = WebPushCrypto.b64dec(rfcSalt) + byteArrayOf(0, 0, 16, 0, 65) +
            WebPushCrypto.b64dec(rfcAsPublic) + WebPushCrypto.b64dec("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ")
        assertEquals("When I grow up, I want to be a watermelon", String(WebPushCrypto.decrypt(body,
            WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcUaPrivate)), WebPushCrypto.b64dec(rfcUaPublic),
            WebPushCrypto.b64dec(rfcAuth))))
    }

    @Test
    fun decrypt_rejectsTamperingWrongKeysAndMalformedHeaders() {
        val privateKey = WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcUaPrivate))
        val publicKey = WebPushCrypto.b64dec(rfcUaPublic)
        val auth = WebPushCrypto.b64dec(rfcAuth)
        val body = WebPushCrypto.encrypt("message".toByteArray(), rfcUaPublic, rfcAuth)
        val malformed = listOf(
            body.copyOf(50), ByteArray(4097),
            body.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            body.copyOf().apply { this[20] = 64 },
            body.copyOf().apply { for (i in 16..19) this[i] = 0 }
        )
        for (input in malformed) assertThrows(Exception::class.java) {
            WebPushCrypto.decrypt(input, privateKey, publicKey, auth)
        }
        assertThrows(Exception::class.java) { WebPushCrypto.decrypt(body, privateKey, publicKey, ByteArray(16)) }
        assertThrows(Exception::class.java) {
            WebPushCrypto.decrypt(body, WebPushCrypto.generateKeyPair().private as ECPrivateKey, publicKey, auth)
        }
    }

    @Test
    fun decrypt_checksAuthenticatedPaddingDelimiter() {
        val sender = KeyPair(WebPushCrypto.loadPublic(WebPushCrypto.b64dec(rfcAsPublic)),
            WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcAsPrivate)))
        val publicKey = WebPushCrypto.b64dec(rfcUaPublic)
        val auth = WebPushCrypto.b64dec(rfcAuth)
        val salt = WebPushCrypto.b64dec(rfcSalt)
        val keys = WebPushCrypto.deriveKeys(WebPushCrypto.ecdh(sender.private as ECPrivateKey,
            WebPushCrypto.loadPublic(publicKey)), auth, publicKey, WebPushCrypto.b64dec(rfcAsPublic), salt)
        fun body(record: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.cek, "AES"), GCMParameterSpec(128, keys.nonce))
            return salt + byteArrayOf(0, 0, 16, 0, 65) + WebPushCrypto.b64dec(rfcAsPublic) + cipher.doFinal(record)
        }
        val privateKey = WebPushCrypto.loadPrivate(WebPushCrypto.b64dec(rfcUaPrivate))
        assertArrayEquals(byteArrayOf(42), WebPushCrypto.decrypt(body(byteArrayOf(42, 2, 0, 0)), privateKey, publicKey, auth))
        for (record in listOf(byteArrayOf(42, 1), byteArrayOf(0, 0), byteArrayOf(42, 3, 0))) {
            assertThrows(IllegalArgumentException::class.java) { WebPushCrypto.decrypt(body(record), privateKey, publicKey, auth) }
        }
    }
}
