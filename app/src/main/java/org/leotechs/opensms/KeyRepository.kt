package org.leotechs.opensms

import android.content.Context
import java.security.PublicKey

/**
 * Stores public keys of contacts.
 */
class KeyRepository(context: Context) {
    private val prefs = context.getSharedPreferences("contact_keys", Context.MODE_PRIVATE)

    /**
     * Saves a contact's public key.
     */
    fun saveKey(address: String, base64Key: String) {
        val cleanAddress = normalizeAddress(address)
        prefs.edit().putString(cleanAddress, base64Key).apply()
    }

    /**
     * Retrieves a contact's public key as a PublicKey object.
     */
    fun getKey(address: String): PublicKey? {
        val cleanAddress = normalizeAddress(address)
        val base64Key = prefs.getString(cleanAddress, null) ?: return null
        return try {
            CryptoUtils.getPublicKeyFromBase64(base64Key)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if we have a key for this address.
     */
    fun hasKey(address: String): Boolean {
        return prefs.contains(normalizeAddress(address))
    }

    private fun normalizeAddress(address: String): String {
        return address.filter { it.isDigit() || it == '+' }
    }
}
