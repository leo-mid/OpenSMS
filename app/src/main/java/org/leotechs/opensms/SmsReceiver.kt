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
            for (sms in messages) {
                val body = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress
                
                // Save to system database
                val repository = SmsRepository(context)
                val threadId = repository.getOrCreateThreadId(sender)
                repository.saveReceivedSms(sender, body)
                
                if (AppState.currentThreadId != threadId) {
                    NotificationHelper.showNotification(context, threadId, sender, body)
                }
                
                Log.d("SmsReceiver", "Saved SMS from $sender")
            }
        }
    }
}
