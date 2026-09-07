package org.leotechs.opensms

import android.annotation.SuppressLint
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// Very simple encryption/decryption tool
// This was just for testing purposes and when implemented will use RSA instead of AES
object CryptoUtils {
    private const val ALGORITHM = "AES"
    private val KEY = "MySecretKey12345".toByteArray() // 16 bytes for AES-128

    @SuppressLint("GetInstance")
    fun encrypt(data: String): String {
        val secretKey = SecretKeySpec(KEY, ALGORITHM)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    @SuppressLint("GetInstance")
    fun decrypt(encryptedData: String): String {
        return try {
            val secretKey = SecretKeySpec(KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            "Error decrypting message"
        }
    }
}
