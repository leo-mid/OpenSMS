package org.leotechs.opensms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return
            
            val fullBody = StringBuilder()
            val sender = messages[0].displayOriginatingAddress ?: return
            
            for (sms in messages) {
                fullBody.append(sms.displayMessageBody)
            }
            
            val body = fullBody.toString()
            val repository = SmsRepository(context)
            
            // Check if number is blocked
            if (repository.isBlocked(sender)) {
                Log.d("SmsReceiver", "Blocked SMS from $sender")
                return
            }
            
            // Save to system database
            val threadId = repository.getOrCreateThreadId(sender)
            
            // Key Exchange Protocol: Detect and save public keys
            if (body.startsWith("[KEY]")) {
                val keyRepo = KeyRepository(context)
                keyRepo.saveKey(sender, body.substring(5))
                Log.d("SmsReceiver", "Saved public key for $sender")
            }
            
            repository.saveReceivedSms(sender, body)
            
            if (AppState.currentThreadId != threadId) {
                NotificationHelper.showNotification(context, threadId, sender, body)
            } else {
                repository.markAsRead(threadId)
                NotificationHelper.cancelNotification(context, threadId)
            }
            
            Log.d("SmsReceiver", "Saved concatenated SMS from $sender")
        }
    }
}
