package org.leotechs.opensms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.mms.pdu_alt.PduParser
import com.google.android.mms.pdu_alt.RetrieveConf
import java.io.File

class MmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("MmsStatusReceiver", "Received action: $action")

        if (MmsUtils.ACTION_MMS_SENT == action) {
            val resultCode = resultCode
            Log.d("MmsStatusReceiver", "MMS Sent Result Code: $resultCode")
            if (resultCode == Activity.RESULT_OK) {
                Log.d("MmsStatusReceiver", "MMS sent successfully")
            } else {
                Log.e("MmsStatusReceiver", "MMS send failed")
            }
        } else if (MmsUtils.ACTION_MMS_DOWNLOADED == action) {
            val resultCode = resultCode
            Log.d("MmsStatusReceiver", "MMS Download Result Code: $resultCode")
            
            val mmsId = intent.getStringExtra("mms_id") ?: return
            val filePath = intent.getStringExtra("file_path") ?: return
            
            if (resultCode == Activity.RESULT_OK) {
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        val data = file.readBytes()
                        Log.d("MmsStatusReceiver", "Downloaded PDU size: ${data.size}")
                        val pdu = PduParser(data).parse()
                        if (pdu is RetrieveConf) {
                            Log.d("MmsStatusReceiver", "MMS $mmsId downloaded successfully, saving parts to DB")
                            SmsRepository(context).saveReceivedMms(mmsId, pdu)
                        } else {
                            Log.e("MmsStatusReceiver", "Downloaded PDU is not RetrieveConf: ${pdu?.javaClass?.simpleName}")
                        }
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("MmsStatusReceiver", "Error processing downloaded MMS", e)
                }
            } else {
                Log.e("MmsStatusReceiver", "MMS Download failed with result code: $resultCode")
            }
        }
    }
}
