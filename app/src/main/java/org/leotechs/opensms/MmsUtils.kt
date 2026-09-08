package org.leotechs.opensms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.mms.pdu_alt.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MmsUtils {
    private const val TAG = "MmsUtils"
    const val ACTION_MMS_SENT = "org.leotechs.opensms.MMS_SENT"
    const val ACTION_MMS_DOWNLOADED = "org.leotechs.opensms.MMS_DOWNLOADED"

    fun sendMms(context: Context, phoneNumber: String, mediaUri: Uri?, bodyText: String? = null, mmsId: Long? = null): Boolean {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // 1. Build the PDU
            val sendReq = SendReq()
            
            // Handle multiple recipients
            val recipients = phoneNumber.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (recipients.isEmpty()) {
                Log.e(TAG, "No recipients provided for MMS")
                return false
            }
            
            recipients.forEach { addr ->
                // Keep the + if present for international numbers
                val cleanAddr = addr.filter { it.isDigit() || it == '+' }
                sendReq.addTo(EncodedStringValue(cleanAddr))
            }
            
            // Standard headers required by many carriers
            // Set FROM address. "insert-address-token" tells the system to fill in the sender's number.
            sendReq.from = EncodedStringValue("insert-address-token")
            
            sendReq.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
            sendReq.expiry = 60 * 60 * 24 * 7
            sendReq.priority = PduHeaders.PRIORITY_NORMAL
            sendReq.date = System.currentTimeMillis() / 1000
            
            // Explicitly set message type and transaction ID
            sendReq.messageType = PduHeaders.MESSAGE_TYPE_SEND_REQ
            sendReq.mmsVersion = PduHeaders.CURRENT_MMS_VERSION
            sendReq.transactionId = ("T" + System.currentTimeMillis().toString(16)).toByteArray()
            
            // Set Content-Type
            sendReq.contentType = "application/vnd.wap.multipart.related".toByteArray()
            
            // Only set a subject if there's text provided
            if (!bodyText.isNullOrBlank()) {
                val subjectText = if (bodyText.length > 30) bodyText.take(30) + "..." else bodyText
                sendReq.subject = EncodedStringValue(subjectText)
            }

            val body = PduBody()
            
            // Add Text part if present
            if (bodyText != null) {
                val textPart = PduPart()
                textPart.contentType = "text/plain".toByteArray()
                textPart.data = bodyText.toByteArray()
                textPart.contentId = "<text>".toByteArray()
                textPart.contentLocation = "text.txt".toByteArray()
                textPart.name = "text.txt".toByteArray()
                textPart.charset = CharacterSets.UTF_8
                body.addPart(textPart)
            }
            
            // Add Media part if present
            if (mediaUri != null) {
                val part = PduPart()
                val contentType = context.contentResolver.getType(mediaUri) ?: "image/jpeg"
                part.contentType = contentType.toByteArray()
                
                var data = context.contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                if (data == null) {
                    Log.e(TAG, "Failed to read media data from $mediaUri")
                    return false
                }

                // Optimization: Resize image if it's too large for MMS (usually ~1MB limit)
                if (contentType.startsWith("image/") && data.size > 600 * 1024) {
                    try {
                        Log.d(TAG, "Resizing large image: ${data.size} bytes")
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(data, 0, data.size, options)
                        
                        var inSampleSize = 1
                        while (options.outWidth / (inSampleSize * 2) >= 1024 && options.outHeight / (inSampleSize * 2) >= 1024) {
                            inSampleSize *= 2
                        }
                        
                        options.inJustDecodeBounds = false
                        options.inSampleSize = inSampleSize
                        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                        
                        if (bitmap != null) {
                            val outputStream = ByteArrayOutputStream()
                            // Compress to JPEG with 70% quality
                            var quality = 80
                            var compressedData: ByteArray
                            do {
                                outputStream.reset()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                                compressedData = outputStream.toByteArray()
                                quality -= 10
                            } while (compressedData.size > 600 * 1024 && quality > 10)
                            
                            data = compressedData
                            Log.d(TAG, "Resized image to: ${data.size} bytes (sampleSize=$inSampleSize, quality=$quality)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resize image", e)
                    }
                }
                
                part.data = data
                part.contentLocation = "media".toByteArray()
                part.name = "media".toByteArray()
                part.contentId = "<media>".toByteArray()
                body.addPart(part)
            }
            
            sendReq.body = body

            val composer = PduComposer(context, sendReq)
            val pduBytes = try {
                composer.make()
            } catch (e: Exception) {
                Log.e(TAG, "PduComposer failed", e)
                null
            }

            if (pduBytes == null) {
                Log.e(TAG, "Failed to compose PDU (result was null)")
                return false
            }
            Log.d(TAG, "Composed PDU size: ${pduBytes.size} bytes for $phoneNumber")

            // 2. Save PDU to a temporary file
            val pduFile = File(context.cacheDir, "send_${System.currentTimeMillis()}.pdu")
            FileOutputStream(pduFile).use { it.write(pduBytes) }

            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pduFile
            )

            // 3. Prepare PendingIntent for status
            val requestCode = mmsId?.toInt() ?: phoneNumber.hashCode()
            val sentIntent = PendingIntent.getBroadcast(
                context, requestCode, 
                Intent(ACTION_MMS_SENT).apply { 
                    setPackage(context.packageName)
                    putExtra("address", phoneNumber)
                    if (mmsId != null) putExtra("mms_id", mmsId)
                }, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 4. Send via SmsManager
            // Grant permission to both the phone app and the messaging provider
            context.grantUriPermission("com.android.phone", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.grantUriPermission("com.android.providers.telephony", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            smsManager.sendMultimediaMessage(context, pduUri, null, null, sentIntent)
            
            Log.d(TAG, "MMS sent request triggered for $phoneNumber with URI $pduUri, ID: $mmsId")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error in sendMms", e)
            return false
        }
    }

    fun downloadMms(context: Context, contentLocation: String, mmsId: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val downloadFile = File(context.cacheDir, "download_$mmsId.pdu")
            if (downloadFile.exists()) downloadFile.delete()
            downloadFile.createNewFile()

            val downloadUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                downloadFile
            )

            val downloadIntent = Intent(ACTION_MMS_DOWNLOADED).apply {
                setPackage(context.packageName)
                putExtra("mms_id", mmsId)
                putExtra("file_path", downloadFile.absolutePath)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context, mmsId.hashCode(), downloadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            context.grantUriPermission("com.android.phone", downloadUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

            smsManager.downloadMultimediaMessage(context, contentLocation, downloadUri, null, pendingIntent)
            Log.d(TAG, "MMS download triggered for $contentLocation, file: ${downloadFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadMms", e)
        }
    }
}
