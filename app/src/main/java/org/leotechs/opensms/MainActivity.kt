package org.leotechs.opensms

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.leotechs.opensms.ui.theme.OpenSMSTheme

class MainActivity : ComponentActivity() {
    private val REQUEST_DEFAULT_APP = 101
    private val MY_PERMISSIONS_REQUEST_SMS = 102
    private var isDefaultSmsApp by mutableStateOf(false)
    private var currentThreadId by mutableStateOf<Long?>(null)
    private var currentContactName by mutableStateOf<String?>(null)
    private var isCreatingNewConversation by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)
        checkDefaultSmsStatus()

        val intentThreadId = intent.getLongExtra("THREAD_ID", -1L)
        if (intentThreadId != -1L) {
            currentThreadId = intentThreadId
            AppState.currentThreadId = intentThreadId
            SmsRepository(this).markAsRead(intentThreadId)
        }

        if (!isDefaultSmsApp) {
            requestDefaultSmsRole()
        } else {
            checkSmsPermissions()
        }

        enableEdgeToEdge()
        setContent {
            OpenSMSTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when {
                            isCreatingNewConversation -> {
                                NewConversationScreen(
                                    onBack = { isCreatingNewConversation = false },
                                    onMessageSent = { threadId, address ->
                                        currentThreadId = threadId
                                        AppState.currentThreadId = threadId
                                        isCreatingNewConversation = false
                                        val contactInfo = SmsRepository(this@MainActivity).getContactInfo(address)
                                        currentContactName = contactInfo.first
                                    },
                                    onSendSms = { number, message, encrypt ->
                                        sendSms(number, message, encrypt)
                                    },
                                    onSendMms = { number, uri ->
                                        sendMms(number, uri)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            currentThreadId == null -> {
                                ConversationList(
                                    isDefault = isDefaultSmsApp,
                                    onConversationClick = { threadId, name ->
                                        currentThreadId = threadId
                                        AppState.currentThreadId = threadId
                                        currentContactName = name
                                        SmsRepository(this@MainActivity).markAsRead(threadId)
                                    },
                                    onRequestDefault = { requestDefaultSmsRole() },
                                    onNewConversation = { isCreatingNewConversation = true },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                MessageDetail(
                                    threadId = currentThreadId!!,
                                    contactName = currentContactName,
                                    onBack = { 
                                        currentThreadId = null 
                                        AppState.currentThreadId = null
                                    },
                                    onSendSms = { number: String, message: String, encrypt: Boolean ->
                                        sendSms(number, message, encrypt)
                                    },
                                    onSendMms = { number: String, uri: Uri ->
                                        sendMms(number, uri)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkDefaultSmsStatus() {
        val roleManager = getSystemService(RoleManager::class.java)
        isDefaultSmsApp = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun requestDefaultSmsRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            startActivityForResult(intent, REQUEST_DEFAULT_APP)
        }
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.getLongExtra("THREAD_ID", -1L)?.let { threadId ->
            if (threadId != -1L) {
                currentThreadId = threadId
                AppState.currentThreadId = threadId
                SmsRepository(this).markAsRead(threadId)
            }
        }
    }

    private fun sendSms(phoneNumber: String, message: String, encrypt: Boolean): Boolean {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            val finalMessage = if (encrypt) {
                "[ENC]${CryptoUtils.encrypt(message)}"
            } else {
                message
            }

            val repository = SmsRepository(this)
            val threadId = repository.getOrCreateThreadId(phoneNumber)

            smsManager.sendTextMessage(phoneNumber, null, finalMessage, null, null)
            repository.saveSentSms(phoneNumber, finalMessage, threadId)
            Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun sendMms(phoneNumber: String, uri: Uri): Boolean {
        try {
            val repository = SmsRepository(this)
            val threadId = repository.getOrCreateThreadId(phoneNumber)

            // 1. Send via network
            MmsUtils.sendMms(this, phoneNumber, uri)
            
            // 2. Save to database
            repository.saveSentMms(phoneNumber, uri, threadId)
            
            Toast.makeText(this, "MMS Sending...", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to send MMS", e)
            Toast.makeText(this, "Failed to send MMS: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun checkSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), MY_PERMISSIONS_REQUEST_SMS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_APP) {
            if (resultCode == RESULT_OK) {
                isDefaultSmsApp = true
                Toast.makeText(this, "OpenSMS is now your default SMS app!", Toast.LENGTH_SHORT).show()
                checkSmsPermissions()
            } else {
                isDefaultSmsApp = false
                Toast.makeText(this, "Permission denied. App may not function correctly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = if(checkPermissionGranted(requestCode, permissions, grantResults)) "permissions granted" else "some permissions not granted"
        Toast.makeText(this, granted, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionGranted(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean{
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_SMS -> {
                return grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            }
        }
        return false
    }
}
