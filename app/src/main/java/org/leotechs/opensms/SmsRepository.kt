package org.leotechs.opensms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.android.mms.pdu_alt.*

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

                while (it.moveToNext()) {
                    try {
                        val threadId = if (finalThreadIdIndex != -1) it.getLong(finalThreadIdIndex) else 0L
                        var snippet = if (snippetIndex != -1) it.getString(snippetIndex) ?: "" else ""
                        var date = if (dateIndex != -1) it.getLong(dateIndex) else 0
                        
                        // Normalize date: seconds to milliseconds
                        if (date > 0 && date < 1000000000000L) date *= 1000

                        val mmsId = isLastMessageMms(threadId)
                        if (mmsId != null) {
                            val count = getMmsAttachmentCount(mmsId)
                            snippet = "Attachment: $count"
                        }

                        // Ensure snippet is never blank
                        if (snippet.isBlank()) {
                            snippet = "New Message"
                        }

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

    fun getAddressForThread(threadId: Long): String? {
        try {
            // Try MMS first
            val mmsCursor = context.contentResolver.query(
                Uri.parse("content://mms/"),
                arrayOf("_id"),
                "thread_id = ?",
                arrayOf(threadId.toString()),
                "date DESC LIMIT 1"
            )
            mmsCursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_id")
                    if (idx != -1) {
                        val mmsId = it.getLong(idx)
                        val addr = getMmsAddress(mmsId)
                        if (addr != null) return addr
                    }
                }
            }

            // Fallback to SMS
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

    private fun getMmsAddress(mmsId: Long): String? {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val addrIdx = it.getColumnIndex("address")
            if (addrIdx != -1) {
                while (it.moveToNext()) {
                    val address = it.getString(addrIdx)
                    if (address != null && address != "insert-address-token") {
                        return address
                    }
                }
            }
        }
        return null
    }

    fun getMessages(threadId: Long): List<Message> {
        val messages = mutableListOf<Message>()
        
        // Use unified view for ordering but fetch details manually if needed
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
                val idIdx = it.getColumnIndex("_id")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val addressIdx = it.getColumnIndex("address")
                val typeIdx = it.getColumnIndex("type")
                val ctIndex = it.getColumnIndex("ct_t")
                val transportIdx = it.getColumnIndex("transport_type")
                val mTypeIdx = it.getColumnIndex("m_type")
                val msgBoxIdx = it.getColumnIndex("msg_box")

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getLong(idIdx) else 0
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else 0
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    
                    val contentType = if (ctIndex != -1) it.getString(ctIndex) else null
                    val transport = if (transportIdx != -1) it.getString(transportIdx) else null
                    val mType = if (mTypeIdx != -1) it.getInt(mTypeIdx) else -1
                    val bodyText = if (bodyIdx != -1) it.getString(bodyIdx) else null

                    // Broadened MMS detection logic
                    val isMms = (contentType != null && contentType.contains("multipart")) || 
                                transport == "mms" || 
                                mType > 0 || 
                                (bodyText == null && contentType != null)

                    if (isMms) {
                        val msgBox = if (msgBoxIdx != -1) it.getInt(msgBoxIdx) else 1
                        val mmsMedia = getMmsMedia(id)
                        
                        messages.add(
                            Message(
                                id = id,
                                address = if (address.isEmpty()) getMmsAddress(id) ?: "" else address,
                                body = mmsMedia?.first ?: "",
                                date = if (date > 0 && date < 1000000000000L) date * 1000 else date, // Handle sec vs ms
                                type = if (msgBox == 2) 2 else 1,
                                isEncrypted = false,
                                isMms = true,
                                mediaUri = mmsMedia?.second,
                                mediaContentType = mmsMedia?.third
                            )
                        )
                    } else {
                        val body = bodyText ?: ""
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
                                date = if (date > 0 && date < 1000000000000L) date * 1000 else date,
                                type = if (typeIdx != -1) it.getInt(typeIdx) else 1,
                                isEncrypted = isEncrypted,
                                isMms = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying unified messages", e)
        }
        return messages.sortedBy { it.date }
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
                val ct = if (ctIndex != -1) it.getString(ctIndex) else null
                Log.d("SmsRepository", "MMS Part for ID $mmsId - CT: $ct")
                
                if (ct == "text/plain") {
                    body = if (textIndex != -1) it.getString(textIndex) else null
                } else if (ct != null && (ct.startsWith("image/") || ct.startsWith("video/"))) {
                    val partId = if (idIndex != -1) it.getLong(idIndex) else 0
                    mediaUri = Uri.parse("content://mms/part/$partId")
                    contentType = ct
                    Log.d("SmsRepository", "Found Media part: $mediaUri ($ct)")
                }
            }
        }
        
        return if (body != null || mediaUri != null) {
            Triple(body, mediaUri, contentType)
        } else {
            null
        }
    }

    private fun getMmsAttachmentCount(mmsId: Long): Int {
        val uri = Uri.parse("content://mms/part")
        return try {
            context.contentResolver.query(
                uri,
                arrayOf("_id"),
                "mid = ? AND ct != 'application/smil' AND ct != 'text/plain'",
                arrayOf(mmsId.toString()),
                null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting attachment count for MMS $mmsId", e)
            0
        }
    }

    private fun isLastMessageMms(threadId: Long): Long? {
        // Try unified conversation view first as it's most accurate for what the system thinks is latest
        try {
            val uri = Uri.parse("content://mms-sms/conversations/$threadId")
            context.contentResolver.query(
                uri,
                null,
                null,
                null,
                "date DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val transportIdx = cursor.getColumnIndex("transport_type")
                    val ctIdx = cursor.getColumnIndex("ct_t")
                    val mTypeIdx = cursor.getColumnIndex("m_type")
                    val idIdx = cursor.getColumnIndex("_id")
                    
                    val transport = if (transportIdx != -1) cursor.getString(transportIdx) else null
                    val ct = if (ctIdx != -1) cursor.getString(ctIdx) else null
                    val mType = if (mTypeIdx != -1) cursor.getInt(mTypeIdx) else -1
                    
                    val isMms = transport == "mms" || (ct != null && ct.contains("multipart")) || mType > 0
                    if (isMms && idIdx != -1) {
                        return cursor.getLong(idIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "Unified query failed for thread $threadId, falling back")
        }

        var lastSmsDate = -1L
        var lastMmsDate = -1L
        var lastMmsId = -1L

        // 1. Query latest SMS
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.DATE),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastSmsDate = cursor.getLong(0)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying last SMS for thread $threadId", e)
        }

        // 2. Query latest MMS
        try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    lastMmsId = cursor.getLong(0)
                    lastMmsDate = cursor.getLong(1)
                    // Normalize MMS date: seconds to milliseconds
                    if (lastMmsDate > 0 && lastMmsDate < 1000000000000L) {
                        lastMmsDate *= 1000
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error querying last MMS for thread $threadId", e)
        }

        // 3. Compare dates. If MMS is newer or close to SMS (buffer for precision), return its ID.
        // We use a 2-second buffer because MMS date is often in seconds while SMS is in ms.
        return if (lastMmsId != -1L && (lastSmsDate == -1L || lastMmsDate >= (lastSmsDate - 2000))) {
            lastMmsId
        } else {
            null
        }
    }

    fun searchContacts(query: String): List<Contact> {
        if (query.isBlank()) return emptyList()
        val contacts = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx)
                    val number = cursor.getString(numberIdx)
                    val photo = cursor.getString(photoIdx)
                    contacts.add(Contact(name, number, photo))
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error searching contacts", e)
        }
        return contacts.distinctBy { it.number }
    }

    fun getContactInfo(phoneNumber: String): Pair<String?, String?> {
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

    fun getOrCreateThreadId(address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting/creating thread ID", e)
            0L
        }
    }

    fun saveSentSms(address: String, body: String, threadId: Long? = null) {
        val finalThreadId = threadId ?: getOrCreateThreadId(address)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.THREAD_ID, finalThreadId)
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

    fun saveSentMms(address: String, mediaUri: Uri, threadId: Long? = null) {
        try {
            val finalThreadId = threadId ?: getOrCreateThreadId(address)

            // 1. Insert MMS header
            val values = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, finalThreadId)
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

    fun saveReceivedMms(mmsId: String, pdu: RetrieveConf) {
        try {
            val persister = PduPersister.getPduPersister(context)
            
            // Try to get sub_id from the notification message to maintain consistency
            val subId = try {
                context.contentResolver.query(
                    Uri.parse("content://mms/$mmsId"),
                    arrayOf("sub_id"),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val subIdIndex = cursor.getColumnIndex("sub_id")
                        if (subIdIndex != -1) cursor.getInt(subIdIndex) else 0
                    } else 0
                } ?: 0
            } catch (e: Exception) {
                0 // Default to 0 if sub_id column is not found or query fails
            }

            // Using persist() will handle creating the MMS message and all its parts correctly,
            // avoiding the "Column _data not found" issue with manual openOutputStream.
            // subscriptionId is required for multi-SIM support.
            val uri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
            
            if (uri != null) {
                // Delete the old placeholder notification message
                context.contentResolver.delete(Uri.parse("content://mms/$mmsId"), null, null)
                Log.d("SmsRepository", "Successfully persisted received MMS to $uri and removed notification $mmsId")
            } else {
                Log.e("SmsRepository", "Failed to persist MMS using PduPersister")
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving received MMS", e)
        }
    }
}
