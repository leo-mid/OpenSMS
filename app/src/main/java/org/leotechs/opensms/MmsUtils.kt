package org.leotechs.opensms

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.mms.pdu_alt.*
import java.io.File
import java.io.FileOutputStream

object MmsUtils {
    private const val TAG = "MmsUtils"

    fun sendMms(context: Context, phoneNumber: String, mediaUri: Uri) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // 1. Build the PDU
            val sendReq = SendReq()
            sendReq.addTo(EncodedStringValue(phoneNumber))
            
            val body = PduBody()
            val part = PduPart()
            
            // Get content type
            val contentType = context.contentResolver.getType(mediaUri) ?: "image/jpeg"
            part.contentType = contentType.toByteArray()
            
            // Get data
            val data = context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
            if (data == null) {
                Log.e(TAG, "Failed to read media data from $mediaUri")
                return
            }
            part.data = data
            part.contentLocation = "media".toByteArray()
            part.contentId = "media".toByteArray()
            
            body.addPart(part)
            sendReq.body = body

            val composer = PduComposer(context, sendReq)
            val pduBytes = composer.make()

            if (pduBytes == null) {
                Log.e(TAG, "Failed to compose PDU")
                return
            }

            // 2. Save PDU to a temporary file
            val pduFile = File(context.cacheDir, "send_${System.currentTimeMillis()}.pdu")
            FileOutputStream(pduFile).use { it.write(pduBytes) }

            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pduFile
            )

            // 3. Send via SmsManager
            smsManager.sendMultimediaMessage(context, pduUri, null, null, null)
            
            Log.d(TAG, "MMS sent to $phoneNumber via SmsManager")

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMms", e)
        }
    }
}
