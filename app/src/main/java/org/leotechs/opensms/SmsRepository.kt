package org.leotechs.opensms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import java.util.Date

import android.util.Log

class SmsRepository(private val context: Context) {

    fun getConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val contentResolver = context.contentResolver
        
        try {
            // Query threads using mms-sms provider for a unified view
            val uri = Uri.parse("content://mms-sms/conversations?simple=true")
            val cursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val threadIdIndex = it.getColumnIndex("_id")
                val snippetIndex = it.getColumnIndex("snippet")
                val dateIndex = it.getColumnIndex("date")

                // If _id is not found, try thread_id
                val finalThreadIdIndex = if (threadIdIndex != -1) threadIdIndex else it.getColumnIndex("thread_id")

                if (finalThreadIdIndex == -1) {
                    Log.e("SmsRepository", "Could not find thread ID column")
                    return@use
                }

                while (it.moveToNext()) {
                    try {
                        val threadId = it.getLong(finalThreadIdIndex)
                        val snippet = if (snippetIndex != -1) it.getString(snippetIndex) ?: "" else ""
                        val date = if (dateIndex != -1) it.getLong(dateIndex) else 0
                        
                        val address = getAddressForThread(threadId) ?: "Unknown"
                        val contactInfo = getContactInfo(address)

                        val isEncrypted = snippet.startsWith("[ENC]")
                        val displaySnippet = if (isEncrypted) {
                            "[Decrypted] " + CryptoUtils.decrypt(snippet.substring(5))
                        } else {
                            snippet
                        }

                        conversations.add(
                            Conversation(
                                threadId = threadId,
                                address = address,
                                snippet = displaySnippet,
                                date = date,
                                contactName = contactInfo.first,
                                contactPhotoUri = contactInfo.second,
                                isEncrypted = isEncrypted
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("SmsRepository", "Error processing conversation row", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying conversations", e)
        }
        return conversations
    }

    private fun getAddressForThread(threadId: Long): String? {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("address"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting address for thread $threadId", e)
        }
        return null
    }

    fun getMessages(threadId: Long): List<Message> {
        val messages = mutableListOf<Message>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                val addressIndex = it.getColumnIndex("address")
                val typeIndex = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getLong(idIndex) else 0
                    val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else 0
                    val address = if (addressIndex != -1) it.getString(addressIndex) ?: "" else ""
                    val type = if (typeIndex != -1) it.getInt(typeIndex) else 1

                    val isEncrypted = body.startsWith("[ENC]")
                    val displayBody = if (isEncrypted) {
                        "[Decrypted] " + CryptoUtils.decrypt(body.substring(5))
                    } else {
                        body
                    }

                    messages.add(
                        Message(
                            id = id,
                            address = address,
                            body = displayBody,
                            date = date,
                            type = type,
                            isEncrypted = isEncrypted
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying messages for thread $threadId", e)
        }
        return messages
    }

    private fun getContactInfo(phoneNumber: String): Pair<String?, String?> {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                val photoUri = cursor.getString(1)
                return Pair(name, photoUri)
            }
        }
        return Pair(null, null)
    }
}
