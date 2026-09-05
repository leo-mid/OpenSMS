package org.leotechs.opensms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.Toast

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION || 
            intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val body = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress

                val processedBody = if (body.startsWith("[ENC]")) {
                    val encryptedPart = body.substring(5)
                    "[Decrypted] " + CryptoUtils.decrypt(encryptedPart)
                } else {
                    body
                }

                Toast.makeText(context, "From $sender: $processedBody", Toast.LENGTH_LONG).show()
            }
        }
    }
}
