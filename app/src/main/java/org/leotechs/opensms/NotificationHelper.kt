package org.leotechs.opensms

//noinspection SuspiciousImport
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

object NotificationHelper {
    private const val CHANNEL_ID = "messages_channel"
    private const val CHANNEL_NAME = "Messages"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new SMS and MMS messages"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, threadId: Long, sender: String, messageBody: String) {
        val repository = SmsRepository(context)
        val isGroup = repository.isGroupThread(threadId)
        val contactInfo = repository.getContactInfo(sender)
        val senderName = contactInfo.first ?: sender
        val photoUri = contactInfo.second

        val title = if (isGroup) {
            val groupName = repository.getThreadName(threadId) ?: "Group Chat"
            "$senderName in $groupName"
        } else {
            senderName
        }

        // Create intent to open the app and the specific conversation
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("THREAD_ID", threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info) // Use a proper icon later
            .setContentTitle(title)
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (photoUri != null) {
            try {
                val uri = photoUri.toUri()
                val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
                builder.setLargeIcon(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(threadId.toInt(), builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    fun cancelNotification(context: Context, threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }
}
