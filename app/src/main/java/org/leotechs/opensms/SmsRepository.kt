package org.leotechs.opensms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.google.android.mms.pdu_alt.*
import kotlin.math.abs
import androidx.core.net.toUri

class SmsRepository(private val context: Context) {

    private val contactCache = mutableMapOf<String, Pair<String?, String?>>()
    private val canonicalAddressCache = mutableMapOf<Long, String>()

    fun clearContactCache() {
        contactCache.clear()
        canonicalAddressCache.clear()
    }

    fun getConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        
        // Pre-fetch data to avoid sub-queries in the loop
        loadCanonicalAddresses()
        val lastMmsInfo = getLastMmsInfo()
        val attachmentCounts = getAttachmentCounts()

        try {
            // Using a specific projection for performance
            val projection = arrayOf("_id", "snippet", "date", "read", "recipient_ids")
            val uri = "content://mms-sms/conversations?simple=true".toUri()
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val threadIdIdx = it.getColumnIndex("_id")
                val snippetIdx = it.getColumnIndex("snippet")
                val dateIdx = it.getColumnIndex("date")
                val readIdx = it.getColumnIndex("read")
                val recipientIdsIdx = it.getColumnIndex("recipient_ids")

                while (it.moveToNext()) {
                    try {
                        val threadId = it.getLong(threadIdIdx)
                        var snippet = it.getString(snippetIdx) ?: ""
                        var date = it.getLong(dateIdx)
                        val isRead = it.getInt(readIdx) == 1
                        
                        // Normalize date: seconds to milliseconds
                        if (date in 1..<1000000000000L) date *= 1000

                        // Optimization: Determine if last message was MMS using pre-fetched data
                        lastMmsInfo[threadId]?.let { (mmsId, mmsDate) ->
                            if (abs(mmsDate - date) < 10000) {
                                val count = attachmentCounts[mmsId] ?: 0
                                // If snippet is empty, it's likely a media-only MMS
                                if (snippet.isBlank() || count > 0) {
                                    snippet = if (count > 0) "Attachment: $count" else "MMS Message"
                                }
                            }
                        }

                        // Ensure snippet is never blank
                        if (snippet.isBlank()) {
                            snippet = "New Message"
                        }

                        // Optimization: Get address from cache or pre-loaded canonical addresses
                        val addresses = recipientIdsIdx.let { idx ->
                            val idsStr = it.getString(idx) ?: ""
                            if (idsStr.isNotEmpty()) {
                                idsStr.split(" ").mapNotNull { idStr ->
                                    canonicalAddressCache[idStr.toLongOrNull() ?: -1L]
                                }
                            } else emptyList()
                        }
                        
                        val isGroup = addresses.size > 1
                        val displayAddress = addresses.joinToString(", ")

                        // Optimization: Use contact cache to avoid repeated ContactsContract queries
                        val contactInfos = addresses.map { addr ->
                            contactCache.getOrPut(addr) {
                                getContactInfo(addr)
                            }
                        }

                        val contactName = if (isGroup) {
                            contactInfos.map { it.first ?: it.second ?: "Unknown" }.joinToString(", ")
                        } else {
                            contactInfos.firstOrNull()?.first
                        }
                        
                        val contactPhotoUri = if (isGroup) null else contactInfos.firstOrNull()?.second

                        val isEncrypted = snippet.startsWith("[ENC]")
                        val displaySnippet = if (isEncrypted) {
                            try {
                                "[Decrypted] " + CryptoUtils.decrypt(snippet.substring(5))
                            } catch (_: Exception) {
                                snippet
                            }
                        } else {
                            snippet
                        }

                        val isBlocked = !isGroup && isBlocked(displayAddress)

                        conversations.add(
                            Conversation(
                                threadId = threadId,
                                address = displayAddress,
                                snippet = displaySnippet,
                                date = date,
                                contactName = contactName,
                                contactPhotoUri = contactPhotoUri,
                                isEncrypted = isEncrypted,
                                isRead = isRead,
                                isGroup = isGroup,
                                isBlocked = isBlocked,
                                addresses = addresses
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

    private fun getMmsAddress(mmsId: Long, type: Int? = null): String? {
        val uri = "content://mms/$mmsId/addr".toUri()
        val selection = if (type != null) "type = ?" else null
        val selectionArgs = if (type != null) arrayOf(type.toString()) else null
        
        val cursor = context.contentResolver.query(uri, null, selection, selectionArgs, null)
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

    fun getMessages(threadId: Long, limit: Int = 30, offset: Int = 0): List<Message> {
        val messages = mutableListOf<Message>()
        
        // Strategy: Query SMS and MMS separately to use SQL LIMIT for performance,
        val fetchCount = limit + offset + 20 

        // 1. Query SMS
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $fetchCount"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val body = cursor.getString(bodyIdx) ?: ""
                    var date = cursor.getLong(dateIdx)
                    val address = cursor.getString(addressIdx) ?: ""
                    val type = cursor.getInt(typeIdx)

                    if (date in 1..<1000000000000L) date *= 1000

                    val isEncrypted = body.startsWith("[ENC]")
                    val displayBody = if (isEncrypted) {
                        try {
                            "[Decrypted] " + CryptoUtils.decrypt(body.substring(5))
                        } catch (_: Exception) { body }
                    } else body

                    messages.add(
                        Message(
                            id = "sms_$id",
                            address = address,
                            body = displayBody,
                            date = date,
                            type = type,
                            isEncrypted = isEncrypted,
                            isMms = false,
                            senderAddress = if (type == 2) null else address
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("SmsRepository", "Error querying SMS", e) }

        // 2. Query MMS
        try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                null,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} DESC LIMIT $fetchCount"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Mms._ID)
                val dateIdx = cursor.getColumnIndex(Telephony.Mms.DATE)
                val msgBoxIdx = cursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    var date = cursor.getLong(dateIdx)
                    val msgBox = cursor.getInt(msgBoxIdx)

                    if (date in 1..<1000000000000L) date *= 1000

                    val mmsMedia = getMmsMedia(id)
                    // Treat anything not in the INBOX as a "Sent" message from the user's perspective
                    val isSent = msgBox != 1 
                    val otherPartyAddress = if (!isSent) getMmsAddress(id, 137) else getMmsAddress(id, 151)

                    messages.add(
                        Message(
                            id = "mms_$id",
                            address = otherPartyAddress ?: "Unknown",
                            body = mmsMedia?.first ?: "",
                            date = date,
                            type = if (isSent) 2 else 1,
                            isEncrypted = false,
                            isMms = true,
                            mediaUri = mmsMedia?.second,
                            mediaContentType = mmsMedia?.third,
                            senderAddress = if (!isSent) getMmsAddress(id, 137) else null
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("SmsRepository", "Error querying MMS", e) }

        // 3. Merge, Sort and Paginate
        val allSorted = messages.sortedByDescending { it.date }
        val start = offset.coerceIn(0, allSorted.size)
        val end = (offset + limit).coerceIn(0, allSorted.size)
        
        return allSorted.subList(start, end)
    }

    private fun getMmsMedia(mmsId: Long): Triple<String?, Uri?, String?>? {
        val selectionPart = "mid = ?"
        val uri = "content://mms/part".toUri()
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
                    mediaUri = "content://mms/part/$partId".toUri()
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

    private fun loadCanonicalAddresses() {
        if (canonicalAddressCache.isNotEmpty()) return
        try {
            val uri = "content://mms-sms/canonical-addresses".toUri()
            context.contentResolver.query(uri, arrayOf("_id", "address"), null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val addrIdx = cursor.getColumnIndex("address")
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val addr = cursor.getString(addrIdx)
                    if (addr != null) canonicalAddressCache[id] = addr
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error loading canonical addresses", e)
        }
    }

    private fun getLastMmsInfo(): Map<Long, Pair<Long, Long>> {
        val map = mutableMapOf<Long, Pair<Long, Long>>() // threadId -> (mmsId, date)
        try {
            val cursor = context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID, Telephony.Mms._ID, Telephony.Mms.DATE),
                null, null, "date DESC"
            )
            cursor?.use {
                val threadIdIdx = it.getColumnIndex(Telephony.Mms.THREAD_ID)
                val idIdx = it.getColumnIndex(Telephony.Mms._ID)
                val dateIdx = it.getColumnIndex(Telephony.Mms.DATE)
                while (it.moveToNext()) {
                    val threadId = it.getLong(threadIdIdx)
                    if (!map.containsKey(threadId)) {
                        var date = it.getLong(dateIdx)
                        if (date in 1..<1000000000000L) date *= 1000
                        map[threadId] = Pair(it.getLong(idIdx), date)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error pre-fetching last MMS info", e)
        }
        return map
    }

    private fun getAttachmentCounts(): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        try {
            val uri = "content://mms/part".toUri()
            val cursor = context.contentResolver.query(
                uri,
                arrayOf("mid"),
                "ct != 'application/smil' AND ct != 'text/plain'",
                null, null
            )
            cursor?.use {
                val midIdx = it.getColumnIndex("mid")
                while (it.moveToNext()) {
                    val mid = it.getLong(midIdx)
                    map[mid] = (map[mid] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error pre-fetching attachment counts", e)
        }
        return map
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

    fun getDetailedContact(phoneNumber: String): Contact {
        val info = getContactInfo(phoneNumber)
        return Contact(
            name = info.first ?: phoneNumber,
            number = phoneNumber,
            photoUri = info.second
        )
    }

    fun getContactLookupUri(phoneNumber: String): Uri? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.LOOKUP_KEY
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val lookupKey = cursor.getString(1)
                    return ContactsContract.Contacts.getLookupUri(id, lookupKey)
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting contact lookup URI", e)
        }
        return null
    }

    fun getAddressesForThread(threadId: Long): List<String> {
        val addresses = mutableListOf<String>()
        try {
            // Query the threads table directly for the recipient_ids
            val uri = "content://mms-sms/conversations?simple=true".toUri()
            context.contentResolver.query(uri, arrayOf("recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val recipientIds = cursor.getString(0) ?: ""
                    if (recipientIds.isNotEmpty()) {
                        loadCanonicalAddresses()
                        recipientIds.split(" ").forEach { idStr ->
                            val addr = canonicalAddressCache[idStr.toLongOrNull() ?: -1L]
                            if (addr != null && addr != "insert-address-token") {
                                addresses.add(addr)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting addresses for thread $threadId", e)
        }
        
        // If the above failed or returned nothing, try to find addresses from messages in this thread
        if (addresses.isEmpty()) {
            try {
                val uri = "content://mms-sms/conversations/$threadId".toUri()
                context.contentResolver.query(uri, arrayOf("address"), "address IS NOT NULL", null, "date DESC LIMIT 5")?.use { cursor ->
                    val addrIdx = cursor.getColumnIndex("address")
                    while (cursor.moveToNext()) {
                        val addr = cursor.getString(addrIdx)
                        if (addr != null && !addresses.contains(addr)) {
                            addresses.add(addr)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsRepository", "Fallback address fetch failed", e)
            }
        }
        
        return addresses.distinct()
    }

    fun getOrCreateThreadId(address: String): Long {
        return try {
            val addresses = address.split(",").map { it.trim() }.toSet()
            Telephony.Threads.getOrCreateThreadId(context, addresses)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error getting/creating thread ID", e)
            0L
        }
    }

    fun isGroupThread(threadId: Long): Boolean {
        try {
            val uri = "content://mms-sms/conversations?simple=true".toUri()
            context.contentResolver.query(uri, arrayOf("recipient_ids"), "_id = ?", arrayOf(threadId.toString()), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val recipientIds = cursor.getString(0) ?: ""
                    return recipientIds.split(" ").size > 1
                }
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error checking if group thread", e)
        }
        return false
    }

    fun getThreadName(threadId: Long): String? {
        val addresses = getAddressesForThread(threadId)
        if (addresses.isEmpty()) return null
        
        if (addresses.size == 1) {
            return getContactInfo(addresses[0]).first ?: addresses[0]
        }
        
        return addresses.map { addr ->
            getContactInfo(addr).first ?: addr
        }.joinToString(", ")
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

    fun saveSentMms(address: String, mediaUri: Uri?, bodyText: String? = null, threadId: Long? = null): Long {
        try {
            val finalThreadId = threadId ?: getOrCreateThreadId(address)

            // 1. Insert MMS header
            val values = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, finalThreadId)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX) // Set to OUTBOX until sent
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                if (bodyText != null) {
                    put(Telephony.Mms.SUBJECT, bodyText)
                }
            }
            val mmsUri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
            if (mmsUri == null) {
                Log.e("SmsRepository", "Failed to insert MMS header")
                return -1L
            }
            val mmsId = mmsUri.lastPathSegment?.toLongOrNull() ?: -1L
            val mmsIdStr = mmsId.toString()

            // 2. Insert recipient addresses
            address.split(",").map { it.trim() }.forEach { addr ->
                val addrValues = ContentValues().apply {
                    put("address", addr)
                    put("type", 151) // PDU_ADDR_TYPE_TO
                    put("charset", 106) // UTF-8
                }
                context.contentResolver.insert("content://mms/$mmsIdStr/addr".toUri(), addrValues)
            }

            // 2b. Insert "insert-address-token" as the FROM address locally to help system grouping
            val fromValues = ContentValues().apply {
                put("address", "insert-address-token")
                put("type", 137) // PDU_ADDR_TYPE_FROM
                put("charset", 106)
            }
            context.contentResolver.insert("content://mms/$mmsIdStr/addr".toUri(), fromValues)

            // 3. Insert text part if present
            if (bodyText != null) {
                val textValues = ContentValues().apply {
                    put("ct", "text/plain")
                    put("text", bodyText)
                }
                context.contentResolver.insert("content://mms/$mmsIdStr/part".toUri(), textValues)
            }

            // 4. Insert media part if present
            if (mediaUri != null) {
                val partValues = ContentValues().apply {
                    put("ct", context.contentResolver.getType(mediaUri) ?: "image/jpeg")
                    put("name", "media")
                    put("cl", "media")
                }
                val partUri = context.contentResolver.insert("content://mms/$mmsIdStr/part".toUri(), partValues)
                if (partUri != null) {
                    context.contentResolver.openOutputStream(partUri)?.use { out ->
                        context.contentResolver.openInputStream(mediaUri)?.use { it.copyTo(out) }
                    }
                }
            }

            Log.d("SmsRepository", "Successfully saved sent MMS to system database: $mmsUri")
            return mmsId
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving sent MMS", e)
            return -1L
        }
    }

    fun saveReceivedMms(mmsId: String, pdu: RetrieveConf) {
        try {
            val persister = PduPersister.getPduPersister(context)
            
            // Try to get sub_id from the notification message to maintain consistency
            val subId = try {
                context.contentResolver.query(
                    "content://mms/$mmsId".toUri(),
                    arrayOf("sub_id"),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val subIdIndex = cursor.getColumnIndex("sub_id")
                        if (subIdIndex != -1) cursor.getInt(subIdIndex) else 0
                    } else 0
                } ?: 0
            } catch (_: Exception) {
                0 // Default to 0 if sub_id column is not found or query fails
            }

            // Using persist() will handle creating the MMS message and all its parts correctly,
            // avoiding the "Column _data not found" issue with manual openOutputStream.
            // subscriptionId is required for multi-SIM support.
            val uri = persister.persist(pdu, Telephony.Mms.Inbox.CONTENT_URI, true, true, null, subId)
            
            if (uri != null) {
                // Delete the old placeholder notification message
                context.contentResolver.delete("content://mms/$mmsId".toUri(), null, null)
                Log.d("SmsRepository", "Successfully persisted received MMS to $uri and removed notification $mmsId")

                // Show notification for the final persisted message
                val threadId = getThreadIdFromUri(uri)
                if (threadId != -1L) {
                    val from = pdu.from?.getString() ?: "Unknown"
                    val snippet = getMmsSnippet(pdu) ?: "New MMS message"
                    
                    if (AppState.currentThreadId != threadId) {
                        NotificationHelper.showNotification(context, threadId, from, snippet)
                    } else {
                        markAsRead(threadId)
                        NotificationHelper.cancelNotification(context, threadId)
                    }
                }
            } else {
                Log.e("SmsRepository", "Failed to persist MMS using PduPersister")
            }
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error saving received MMS", e)
        }
    }

    private fun getThreadIdFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf("thread_id"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun getMmsSnippet(pdu: RetrieveConf): String? {
        val body = pdu.body
        if (body != null) {
            for (i in 0 until body.partsNum) {
                val part = body.getPart(i)
                val ct = String(part.contentType)
                if (ct == "text/plain") {
                    return part.data?.let { String(it) }
                }
            }
        }
        return null
    }

    fun updateMmsBox(mmsId: Long, box: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, box)
        }
        try {
            context.contentResolver.update(
                "content://mms/$mmsId".toUri(),
                values,
                null,
                null
            )
            Log.d("SmsRepository", "Updated MMS $mmsId to box $box")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error updating MMS box", e)
        }
    }

    fun markAsRead(threadId: Long) {
        setReadStatus(threadId, true)
    }

    fun markAsUnread(threadId: Long) {
        setReadStatus(threadId, false)
    }

    private fun setReadStatus(threadId: Long, isRead: Boolean) {
        val values = ContentValues().apply {
            put("read", if (isRead) 1 else 0)
        }
        val selection = "thread_id = ?"
        val selectionArgs = arrayOf(threadId.toString())

        try {
            // Update SMS
            val smsCount = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )
            // Update MMS
            val mmsCount = context.contentResolver.update(
                Telephony.Mms.CONTENT_URI,
                values,
                selection,
                selectionArgs
            )
            Log.d("SmsRepository", "Updated read status to $isRead for thread $threadId. SMS: $smsCount, MMS: $mmsCount")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error setting thread $threadId read status to $isRead", e)
        }
    }

    fun deleteConversation(threadId: Long) {
        try {
            context.contentResolver.delete(
                "content://mms-sms/conversations/$threadId".toUri(),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error deleting conversation:" + e.message)
        }
    }

    fun blockNumber(phoneNumber: String) {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber)
        }
        try {
            context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            Log.d("SmsRepository", "Blocked number: $phoneNumber")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error blocking number: $phoneNumber", e)
        }
    }

    fun unblockNumber(phoneNumber: String) {
        try {
            val selection = "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
            val selectionArgs = arrayOf(phoneNumber)
            context.contentResolver.delete(BlockedNumberContract.BlockedNumbers.CONTENT_URI, selection, selectionArgs)
            Log.d("SmsRepository", "Unblocked number: $phoneNumber")
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error unblocking number: $phoneNumber", e)
        }
    }

    fun isBlocked(phoneNumber: String): Boolean {
        return try {
            BlockedNumberContract.isBlocked(context, phoneNumber)
        } catch (e: Exception) {
            Log.e("SmsRepository", "Error checking if blocked: $phoneNumber", e)
            false
        }
    }

    fun getContactsForThread(threadId: Long): List<Contact> {
        val people = getAddressesForThread(threadId)
        return people.map { getDetailedContact(it) }
    }
}
