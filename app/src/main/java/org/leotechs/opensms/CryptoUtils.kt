package org.leotechs.opensms

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid Encryption Utility (RSA + AES-GCM).
 *
 * Supports large data by:
 * 1. Generating a random AES-256 session key for each encryption.
 * 2. Encrypting the data with AES-GCM (no size limit).
 * 3. Encrypting the session key with RSA (KeyStore backed).
 */
object CryptoUtils {
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALIAS = "OpenSMS_RSA_Key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val RSA_KEY_SIZE_BYTES = 256 // 2048 bit RSA output

    private val keyPair: KeyPair by lazy {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(2048)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setUserAuthenticationRequired(false)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        KeyPair(entry.certificate.publicKey, entry.privateKey)
    }

    /**
     * Encrypts string data. Returns Base64 string.
     */
    fun encrypt(data: String): String {
        val encrypted = encrypt(data.toByteArray())
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Encrypts raw bytes.
     * Output format: [Encrypted AES Key] + [IV] + [Encrypted Data]
     */
    fun encrypt(data: ByteArray): ByteArray {
        return try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE)
            val aesKey = keyGen.generateKey()

            val aesCipher = Cipher.getInstance(AES_ALGORITHM)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val iv = aesCipher.iv
            val encryptedData = aesCipher.doFinal(data)

            val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
            rsaCipher.init(Cipher.ENCRYPT_MODE, keyPair.public)
            val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

            ByteBuffer.allocate(encryptedAesKey.size + iv.size + encryptedData.size)
                .put(encryptedAesKey)
                .put(iv)
                .put(encryptedData)
                .array()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Decrypts Base64 encoded string.
     */
    fun decrypt(encryptedData: String): String {
        return try {
            val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
            String(decrypt(decoded))
        } catch (e: Exception) {
            "Error decrypting message"
        }
    }

    /**
     * Decrypts raw bytes.
     */
    fun decrypt(encryptedData: ByteArray): ByteArray {
        return try {
            val buffer = ByteBuffer.wrap(encryptedData)

            val encryptedAesKey = ByteArray(RSA_KEY_SIZE_BYTES)
            buffer.get(encryptedAesKey)

            val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
            rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)

            val encryptedBytes = ByteArray(buffer.remaining())
            buffer.get(encryptedBytes)

            val aesCipher = Cipher.getInstance(AES_ALGORITHM)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            aesCipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            byteArrayOf()
        }
    }
}
