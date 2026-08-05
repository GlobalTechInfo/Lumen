package com.lumen.app.data.link

import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LumenCrypto {
    private const val GCM_TAG_BITS = 128

    fun generateKeyPair(): KeyPair {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair()
        } catch (_: GeneralSecurityException) {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            return generator.generateKeyPair()
        }
    }

    fun publicKeyBytes(publicKey: PublicKey): ByteArray = publicKey.encoded

    fun encodePublic(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun decodePublic(algorithm: String, encoded: String): PublicKey {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        return KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(raw))
    }

    fun deriveShared(privateKey: java.security.PrivateKey, peerPublic: PublicKey): ByteArray {
        val algorithm = if (privateKey.algorithm == "X25519") "X25519" else "ECDH"
        val agreement = KeyAgreement.getInstance(algorithm)
        agreement.init(privateKey)
        agreement.doPhase(peerPublic, true)
        return agreement.generateSecret()
    }

    fun hkdf(input: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(input)
        val out = ByteArray(length)
        var offset = 0
        var counter = 1
        var block = ByteArray(0)
        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(block)
            mac.update(info)
            mac.update(counter.toByte())
            block = mac.doFinal()
            val copied = minOf(block.size, length - offset)
            System.arraycopy(block, 0, out, offset, copied)
            offset += copied
            counter++
        }
        return out
    }

    fun mac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    fun deriveFromPin(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded
    }

    fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return ByteArray(iv.size + ciphertext.size).also { out ->
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(ciphertext, 0, out, iv.size, ciphertext.size)
        }
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        val iv = sealed.copyOfRange(0, 12)
        val ciphertext = sealed.copyOfRange(12, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun shortId(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return Base64.encodeToString(
            digest.copyOfRange(0, 9),
            Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }
}
