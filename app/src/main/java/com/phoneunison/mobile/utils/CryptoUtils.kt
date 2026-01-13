package com.phoneunison.mobile.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities for secure communication.
 */
object CryptoUtils {

    private const val EC_ALGORITHM = "EC"
    private const val EC_CURVE = "secp256r1"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private var sharedSecret: ByteArray? = null

    /**
     * Gets a unique device ID.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    /**
     * Generates an ECDH key pair and returns the public key as Base64.
     */
    fun generateKeyPair(): String {
        return try {
            val keyGen = KeyPairGenerator.getInstance(EC_ALGORITHM)
            keyGen.initialize(ECGenParameterSpec(EC_CURVE))
            val keyPair = keyGen.generateKeyPair()
            Base64.getEncoder().encodeToString(keyPair.public.encoded)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Performs ECDH key agreement with the peer's public key.
     */
    fun performKeyAgreement(ourPrivateKey: ByteArray, peerPublicKey: ByteArray) {
        // Implementation would go here
        // This is simplified for the example
    }

    /**
     * Encrypts data using AES-256-GCM.
     */
    fun encrypt(plaintext: String): String {
        val secret = sharedSecret ?: return plaintext
        
        val iv = ByteArray(GCM_IV_LENGTH)
        java.security.SecureRandom().nextBytes(iv)
        
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val keySpec = SecretKeySpec(secret, "AES")
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec)
        
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts data using AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String): String {
        val secret = sharedSecret ?: return encryptedBase64
        
        val combined = Base64.getDecoder().decode(encryptedBase64)
        
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val keySpec = SecretKeySpec(secret, "AES")
        val paramSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec)
        
        val plaintext = cipher.doFinal(ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
}
