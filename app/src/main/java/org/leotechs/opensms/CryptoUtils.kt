package org.leotechs.opensms

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hybrid Encryption Utility (RSA + AES-GCM).
 */
object CryptoUtils {
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALIAS = "OpenSMS_RSA_Key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val RSA_KEY_SIZE_BYTES = 256

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
     * Returns the local public key object.
     */
    fun getPublicKey(): PublicKey = keyPair.public

    /**
     * Returns the local public key encoded as Base64.
     */
    fun getLocalPublicKeyBase64(): String {
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Reconstructs a PublicKey object from a Base64 string.
     */
    fun getPublicKeyFromBase64(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    /**
     * Encrypts for multiple recipients (Group E2EE).
     * Output format: 
     * [Num Recipients (Int)] 
     * + [Recipient 1 Public Key Hash (8 bytes)] + [Encrypted AES Key 1 (256 bytes)]
     * + [Recipient 2 Public Key Hash (8 bytes)] + [Encrypted AES Key 2 (256 bytes)]
     * ...
     * + [IV (12 bytes)] + [Encrypted Data]
     */
    fun encryptForRecipients(data: String, publicKeys: List<PublicKey>): String {
        return try {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(AES_KEY_SIZE)
            val aesKey = keyGen.generateKey()

            val aesCipher = Cipher.getInstance(AES_ALGORITHM)
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey)
            val iv = aesCipher.iv
            val encryptedData = aesCipher.doFinal(data.toByteArray())

            val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
            
            // Header: Number of recipients
            val headerSize = 4 + (publicKeys.size * (8 + RSA_KEY_SIZE_BYTES))
            val buffer = ByteBuffer.allocate(headerSize + iv.size + encryptedData.size)
            
            buffer.putInt(publicKeys.size)
            
            for (pubKey in publicKeys) {
                // Use a stable hash for the thumbprint
                val thumbprint = Arrays.hashCode(pubKey.encoded).toLong()
                rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey)
                val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
                
                buffer.putLong(thumbprint)
                buffer.put(encryptedAesKey)
            }

            buffer.put(iv)
            buffer.put(encryptedData)

            Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } catch (e: Exception) {
            "Error encrypting for group"
        }
    }

    /**
     * Decrypts by finding the correct RSA block for our local key.
     */
    fun decrypt(encryptedData: String): String {
        return try {
            val combinedPayload = Base64.decode(encryptedData, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(combinedPayload)
            
            val numRecipients = buffer.getInt()
            val myThumbprint = Arrays.hashCode(keyPair.public.encoded).toLong()
            
            var aesKeyBytes: ByteArray? = null
            
            // Search for the block encrypted for us
            repeat(numRecipients) {
                val thumbprint = buffer.getLong()
                val encryptedBlock = ByteArray(RSA_KEY_SIZE_BYTES)
                buffer.get(encryptedBlock)
                
                if (thumbprint == myThumbprint) {
                    val rsaCipher = Cipher.getInstance(RSA_ALGORITHM)
                    rsaCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
                    aesKeyBytes = rsaCipher.doFinal(encryptedBlock)
                }
            }
            
            if (aesKeyBytes == null) return "Message not encrypted for you"

            val aesKey = SecretKeySpec(aesKeyBytes, "AES")
            val iv = ByteArray(GCM_IV_LENGTH)
            buffer.get(iv)

            val encryptedBytes = ByteArray(buffer.remaining())
            buffer.get(encryptedBytes)

            val aesCipher = Cipher.getInstance(AES_ALGORITHM)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = aesCipher.doFinal(encryptedBytes)

            String(decryptedBytes)
        } catch (e: Exception) {
            "Error decrypting message"
        }
    }
}
