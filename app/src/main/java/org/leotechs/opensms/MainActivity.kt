package org.leotechs.opensms

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
    private var isSettingsOpen by mutableStateOf(false)
    private var isAboutOpen by mutableStateOf(false)
    private var initialRecipient by mutableStateOf<String?>(null)
    private var initialMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)
        checkDefaultSmsStatus()

        handleIntent(intent)

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
                            isAboutOpen -> {
                                AboutScreen(
                                    onBack = { isAboutOpen = false },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            isSettingsOpen -> {
                                SettingsScreen(
                                    onBack = { isSettingsOpen = false },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            isCreatingNewConversation -> {
                                NewConversationScreen(
                                    onBack = { 
                                        isCreatingNewConversation = false
                                        initialRecipient = null
                                        initialMessage = null
                                    },
                                    onMessageSent = { threadId, address ->
                                        currentThreadId = threadId
                                        AppState.currentThreadId = threadId
                                        isCreatingNewConversation = false
                                        initialRecipient = null
                                        initialMessage = null
                                        val contactInfo = SmsRepository(this@MainActivity).getContactInfo(address)
                                        currentContactName = contactInfo.first
                                    },
                                    onSendSms = { number, message, encrypt ->
                                        sendSms(number, message, encrypt)
                                    },
                                    onSendMms = { number, uri ->
                                        sendMms(number, uri)
                                    },
                                    initialRecipient = initialRecipient,
                                    initialMessage = initialMessage,
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
                                        NotificationHelper.cancelNotification(this@MainActivity, threadId)
                                    },
                                    onRequestDefault = { requestDefaultSmsRole() },
                                    onNewConversation = { isCreatingNewConversation = true },
                                    onSettingsClick = { isSettingsOpen = true },
                                    onAboutClick = { isAboutOpen = true },
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
                                        initialMessage = null
                                    },
                                    onSendSms = { number: String, message: String, encrypt: Boolean ->
                                        sendSms(number, message, encrypt)
                                    },
                                    onSendMms = { number: String, uri: Uri? ->
                                        sendMms(number, uri)
                                    },
                                    initialMessage = initialMessage,
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
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        Log.d("MainActivity", "Handling intent: ${intent.action}, data: ${intent.data}")
        
        val intentThreadId = intent.getLongExtra("THREAD_ID", -1L)
        if (intentThreadId != -1L) {
            currentThreadId = intentThreadId
            AppState.currentThreadId = intentThreadId
            SmsRepository(this).markAsRead(intentThreadId)
            NotificationHelper.cancelNotification(this, intentThreadId)
            isCreatingNewConversation = false
            return
        }

        val action = intent.action
        val data = intent.data
        
        if (action == Intent.ACTION_SENDTO || action == Intent.ACTION_VIEW) {
            var address = data?.schemeSpecificPart?.substringBefore('?')
            if (address.isNullOrBlank()) {
                address = intent.getStringExtra("address") ?: intent.getStringExtra(Intent.EXTRA_EMAIL)
            }
            
            val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            
            if (!address.isNullOrBlank()) {
                // Normalize address: remove 'sms:', 'smsto:', etc if they leaked into the part
                val cleanAddress = address.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms:").removePrefix("mmsto:")
                
                val repository = SmsRepository(this)
                val threadId = repository.findThreadId(cleanAddress)
                if (threadId != -1L) {
                    currentThreadId = threadId
                    AppState.currentThreadId = threadId
                    currentContactName = repository.getContactInfo(cleanAddress).first
                    repository.markAsRead(threadId)
                    NotificationHelper.cancelNotification(this, threadId)
                    isCreatingNewConversation = false
                    initialMessage = body
                } else {
                    initialRecipient = cleanAddress
                    initialMessage = body
                    isCreatingNewConversation = true
                    currentThreadId = null
                    AppState.currentThreadId = null
                }
            } else if (action == Intent.ACTION_VIEW) {
                // Check if URI is a specific thread URI like content://mms-sms/conversations/1
                val threadIdFromUri = if (data?.toString()?.startsWith("content://mms-sms/conversations/") == true) {
                    data.lastPathSegment?.toLongOrNull() ?: -1L
                } else -1L

                if (threadIdFromUri != -1L) {
                    currentThreadId = threadIdFromUri
                    AppState.currentThreadId = threadIdFromUri
                    isCreatingNewConversation = false
                } else {
                    // Just open the app / conversation list
                    currentThreadId = null
                    AppState.currentThreadId = null
                    isCreatingNewConversation = false
                    isSettingsOpen = false
                    isAboutOpen = false
                }
            }
        } else if (action == Intent.ACTION_SEND) {
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!body.isNullOrBlank()) {
                initialMessage = body
                isCreatingNewConversation = true
                currentThreadId = null
                AppState.currentThreadId = null
            }
        }
    }

    private fun sendSms(phoneNumber: String, message: String, encrypt: Boolean): Boolean {
        return try {
            val repository = SmsRepository(this)
            val isGroup = phoneNumber.contains(",")

            if (repository.isBlocked(phoneNumber)) {
                Toast.makeText(this, "This number is blocked.", Toast.LENGTH_LONG).show()
                return false
            }

            val finalMessage = if (encrypt) {
                val keyRepo = KeyRepository(this)
                val recipients = phoneNumber.split(",").map { it.trim() }.toMutableList()
                
                // Add Users OWN public key so  can read their sent messages
                val publicKeys = recipients.mapNotNull { keyRepo.getKey(it) }.toMutableList()
                publicKeys.add(CryptoUtils.getPublicKey())

                if (publicKeys.size <= recipients.size) {
                    Toast.makeText(this, "Missing public keys for some recipients. Send your key first!", Toast.LENGTH_LONG).show()
                    return false
                }

                "[ENC]${CryptoUtils.encryptForRecipients(message, publicKeys)}"
            } else {
                message
            }

            // Group messages always use MMS
            if (isGroup) {
                return sendMms(phoneNumber, null, finalMessage)
            }

            val smsManager = this.getSystemService(SmsManager::class.java)
            val threadId = repository.getOrCreateThreadId(phoneNumber)

            // Handle long messages (like [KEY] exchange or long texts)
            if (finalMessage.length > 160) {
                val parts = smsManager.divideMessage(finalMessage)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, finalMessage, null, null)
            }

            repository.saveSentSms(phoneNumber, finalMessage, threadId)
            Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to send SMS", e)
            Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun sendMms(phoneNumber: String, uri: Uri?, bodyText: String? = null): Boolean {
        try {
            val repository = SmsRepository(this)
            val threadId = repository.getOrCreateThreadId(phoneNumber)

            if (repository.isBlocked(phoneNumber)){
                Toast.makeText(this, "This number is blocked.", Toast.LENGTH_LONG).show()
                return false
            }

            // 1. Save to database first in OUTBOX
            val mmsId = repository.saveSentMms(phoneNumber, uri, bodyText, threadId)
            
            if (mmsId != -1L) {
                // 2. Send via network
                val triggered = MmsUtils.sendMms(this, phoneNumber, uri, bodyText, mmsId)
                
                if (triggered) {
                    Toast.makeText(this, "MMS Sending...", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    // If triggering failed, we might want to mark it as failed in DB
                    Toast.makeText(this, "Failed to initiate MMS sending. Check logs.", Toast.LENGTH_LONG).show()
                    return false
                }
            } else {
                Toast.makeText(this, "Failed to save MMS to database.", Toast.LENGTH_LONG).show()
                return false
            }
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

    @Deprecated("Deprecated in Java")
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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = if(checkPermissionGranted(requestCode, grantResults)) "permissions granted" else "some permissions not granted"
        Toast.makeText(this, granted, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionGranted(requestCode: Int, grantResults: IntArray): Boolean{
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_SMS -> {
                return grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            }
        }
        return false
    }
}
