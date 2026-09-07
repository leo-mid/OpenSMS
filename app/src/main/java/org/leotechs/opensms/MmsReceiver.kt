package org.leotechs.opensms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.google.android.mms.pdu_alt.NotificationInd
import com.google.android.mms.pdu_alt.PduParser
import androidx.core.net.toUri

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION &&
            intent.type == "application/vnd.wap.mms-message") {
            
            val data = intent.getByteArrayExtra("data") ?: return
            Log.d("MmsReceiver", "Received WAP PUSH with data size: ${data.size}")

            try {
                val pdu = PduParser(data).parse()
                if (pdu is NotificationInd) {
                    val from = pdu.from?.string ?: "Unknown"
                    val contentLocation = pdu.contentLocation?.let { String(it) } ?: ""
                    
                    Log.d("MmsReceiver", "MMS notification from $from, loc: $contentLocation")
                    
                    val uri = saveMmsNotification(context, pdu)
                    
                    // Don't show notification for the raw NotificationInd, 
                    // wait for the full message to be downloaded in MmsStatusReceiver
                    Log.d("MmsReceiver", "Saved notification for $from, triggering download...")
                    
                    if (uri != null) {
                        val mmsId = uri.lastPathSegment ?: ""
                        MmsUtils.downloadMms(context, contentLocation, mmsId)
                    }
                }
            } catch (e: Exception) {
                Log.e("MmsReceiver", "Error processing MMS notification", e)
            }
        }
    }

    private fun saveMmsNotification(context: Context, pdu: NotificationInd): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            put(Telephony.Mms.READ, 1) // Set as read to avoid "ghost" unread indicators
            put(Telephony.Mms.MESSAGE_TYPE, 130) // m-notification-ind
            put(Telephony.Mms.CONTENT_LOCATION, pdu.contentLocation?.let { String(it) })
            put(Telephony.Mms.TRANSACTION_ID, pdu.transactionId?.let { String(it) })
            put(Telephony.Mms.EXPIRY, pdu.expiry)
            put(Telephony.Mms.MESSAGE_SIZE, pdu.messageSize)
        }
        
        try {
            val uri = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values)
            if (uri != null) {
                val mmsId = uri.lastPathSegment
                val from = pdu.from?.string ?: "Unknown"
                
                val addrValues = ContentValues().apply {
                    put("address", from)
                    put("type", 137) // PDU_ADDR_TYPE_FROM
                    put("charset", 106)
                }
                context.contentResolver.insert("content://mms/$mmsId/addr".toUri(), addrValues)
                Log.d("MmsReceiver", "Successfully saved MMS notification: $uri")
            }
            return uri
        } catch (e: Exception) {
            Log.e("MmsReceiver", "Error saving MMS notification", e)
        }
        return null
    }
}
