package org.leotechs.opensms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsRepository(private val context: Context) {

    fun getConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val contentResolver = context.contentResolver
        
        try {
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
            val uri = Uri.parse("content://mms-sms/conversations/$threadId")
            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "date ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex("_id")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                val addressIndex = it.getColumnIndex("address")
                val typeIndex = it.getColumnIndex("type")
                val ctIndex = it.getColumnIndex("ct")
                val msgBoxIndex = it.getColumnIndex("msg_box")

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getLong(idIndex) else 0
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else 0
                    val address = if (addressIndex != -1) it.getString(addressIndex) ?: "" else ""
                    
                    val contentType = if (ctIndex != -1) it.getString(ctIndex) else null
                    val isMms = contentType != null && contentType != "application/vnd.wap.sic"

                    if (isMms) {
                        val msgBox = if (msgBoxIndex != -1) it.getInt(msgBoxIndex) else 1
                        val mmsMedia = getMmsMedia(id)
                        messages.add(
                            Message(
                                id = id,
                                address = address,
                                body = mmsMedia?.first ?: "",
                                date = date,
                                type = if (msgBox == 2) 2 else 1,
                                isEncrypted = false,
                                isMms = true,
                                mediaUri = mmsMedia?.second,
                                mediaContentType = mmsMedia?.third
                            )
                        )
                    } else {
                        val body = if (bodyIndex != -1) it.getString(bodyIndex) ?: "" else ""
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
                                type = if (typeIndex != -1) it.getInt(typeIndex) else 1,
                                isEncrypted = isEncrypted
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying messages for thread $threadId", e)
        }
        return messages
    }

    private fun getMmsMedia(mmsId: Long): Triple<String?, Uri?, String?>? {
        val selectionPart = "mid = ?"
        val uri = Uri.parse("content://mms/part")
        val cursor = context.contentResolver.query(
            uri,
            null,
            selectionPart,
            arrayOf(mmsId.toString()),
            null
        )

        var body: String? = null
        var mediaUri: Uri? = null
        var contentType: String? = null

        cursor?.use {
            val idIndex = it.getColumnIndex("_id")
            val ctIndex = it.getColumnIndex("ct")
            val textIndex = it.getColumnIndex("text")

            while (it.moveToNext()) {
                val ct = it.getString(ctIndex)
                if (ct == "text/plain") {
                    body = it.getString(textIndex)
                } else if (ct != null && (ct.startsWith("image/") || ct.startsWith("video/"))) {
                    val partId = it.getLong(idIndex)
                    mediaUri = Uri.parse("content://mms/part/$partId")
                    contentType = ct
                }
            }
        }
        
        return if (body != null || mediaUri != null) {
            Triple(body, mediaUri, contentType)
        } else {
            null
        }
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

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photoUri = cursor.getString(1)
                    return Pair(name, photoUri)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting contact info", e)
        }
        return Pair(null, null)
    }

    fun saveSentSms(address: String, body: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.READ, 1)
        }
        try {
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving sent SMS", e)
        }
    }

    fun saveReceivedSms(address: String?, body: String?) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 0)
        }
        try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving received SMS", e)
        }
    }

    fun saveSentMms(address: String, mediaUri: Uri) {
        try {
            // 1. Insert MMS header
            val values = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            }
            val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
            if (mmsUri == null) {
                Log.e("SmsRepository", "Failed to insert MMS header")
                return
            }
            val mmsId = mmsUri.lastPathSegment

            // 2. Insert recipient address
            val addrValues = ContentValues().apply {
                put("address", address)
                put("type", 151) // PDU_ADDR_TYPE_TO
                put("charset", 106) // UTF-8
            }
            context.contentResolver.insert(Uri.parse("content://mms/$mmsId/addr"), addrValues)

            // 3. Insert media part
            val partValues = ContentValues().apply {
                put("ct", context.contentResolver.getType(mediaUri) ?: "image/jpeg")
                put("name", "media")
                put("cl", "media")
            }
            val partUri = context.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"), partValues)
            if (partUri != null) {
                context.contentResolver.openOutputStream(partUri)?.use { out ->
                    context.contentResolver.openInputStream(mediaUri)?.use { it.copyTo(out) }
                }
            }

            Log.d("SmsRepository", "Successfully saved sent MMS to system database: $mmsUri")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving sent MMS", e)
        }
    }
}
