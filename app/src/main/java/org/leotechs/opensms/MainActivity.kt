package org.leotechs.opensms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import org.leotechs.opensms.ui.theme.OpenSMSTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val REQUEST_DEFAULT_APP = 101
    private val MY_PERMISSIONS_REQUEST_SMS = 102
    private var isDefaultSmsApp by mutableStateOf(false)
    private var currentThreadId by mutableStateOf<Long?>(null)
    private var currentContactName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkDefaultSmsStatus()

        if (!isDefaultSmsApp) {
            requestDefaultSmsRole()
        } else {
            checkSmsPermissions()
        }

        enableEdgeToEdge()
        setContent {
            OpenSMSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (currentThreadId == null) {
                        ConversationList(
                            isDefault = isDefaultSmsApp,
                            onConversationClick = { threadId, name ->
                                currentThreadId = threadId
                                currentContactName = name
                            },
                            onRequestDefault = { requestDefaultSmsRole() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        MessageDetail(
                            threadId = currentThreadId!!,
                            contactName = currentContactName,
                            onBack = { currentThreadId = null },
                            onSendSms = { number: String, message: String, encrypt: Boolean ->
                                sendSms(number, message, encrypt)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
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

    private fun sendSms(phoneNumber: String, message: String, encrypt: Boolean): Boolean {
        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            val finalMessage = if (encrypt) {
                "[ENC]${CryptoUtils.encrypt(message)}"
            } else {
                message
            }

            smsManager.sendTextMessage(phoneNumber, null, finalMessage, null, null)
            Toast.makeText(this, "Message sent!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun checkSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.RECEIVE_WAP_PUSH,
            Manifest.permission.READ_CONTACTS
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
            if (resultCode == Activity.RESULT_OK) {
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

@Composable
fun ConversationList(
    isDefault: Boolean,
    onConversationClick: (Long, String?) -> Unit,
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val conversations = remember { mutableStateListOf<Conversation>() }

    LaunchedEffect(Unit) {
        conversations.clear()
        conversations.addAll(repository.getConversations())
    }

    Column(modifier = modifier) {
        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        if (!isDefault) {
            Button(
                onClick = onRequestDefault,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Set as Default SMS App")
            }
        }

        LazyColumn {
            items(conversations) { conversation ->
                ConversationItem(conversation) {
                    onConversationClick(conversation.threadId, conversation.contactName)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    val dateString = remember(conversation.date) {
        formatConversationDate(conversation.date)
    }

    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(
                text = conversation.contactName ?: conversation.address,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(
                text = conversation.snippet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (conversation.contactPhotoUri != null) {
                AsyncImage(
                    model = conversation.contactPhotoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (conversation.contactName ?: conversation.address).take(1).uppercase(),
                        color = Color.White
                    )
                }
            }
        },
        trailingContent = {
            Text(text = dateString, color = Color.Gray)
        }
    )
}

private fun formatConversationDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameDay = now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)

    if (isSameDay) {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) {
        return "Yesterday"
    }

    // Check if it's within the last 6 days (excluding today and yesterday)
    val sixDaysAgo = Calendar.getInstance().apply { 
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6) 
    }
    
    return if (!msgDate.before(sixDaysAgo)) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun MessageDetail(
    threadId: Long,
    contactName: String?,
    onBack: () -> Unit,
    onSendSms: (String, String, Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val messages = remember { mutableStateListOf<Message>() }
    var phoneNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    LaunchedEffect(threadId) {
        val msgs = repository.getMessages(threadId)
        messages.clear()
        messages.addAll(msgs)
        if (msgs.isNotEmpty()) {
            phoneNumber = msgs.last().address
        }
    }

    BoxWithConstraints(modifier = modifier.imePadding()) {
        val maxHeight = maxHeight / 2

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = contactName ?: phoneNumber,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            val listState = rememberLazyListState()
            
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(messages) { message ->
                    MessageItem(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = maxHeight),
                    placeholder = { Text("Message") },
                    maxLines = 100 // High enough to trigger scrolling via heightIn
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(onClick = {
                        if (onSendSms(phoneNumber, messageText, false)) {
                            messageText = ""
                            // Refresh messages
                            val msgs = repository.getMessages(threadId)
                            messages.clear()
                            messages.addAll(msgs)
                        }
                    }) {
                        Text("Send")
                    }
//                    Button(onClick = {
//                        if (onSendSms(phoneNumber, messageText, true)) {
//                            messageText = ""
//                            // Refresh messages
//                            val msgs = repository.getMessages(threadId)
//                            messages.clear()
//                            messages.addAll(msgs)
//                        }
//                    }) {
//                        Text("Enc")
//                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val isSent = message.type == 2 // 2 is Telephony.Sms.MESSAGE_TYPE_SENT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSent) Color(0xFF007AFF) else Color(0xFFE9E9EB))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.body,
                color = if (isSent) Color.White else Color.Black
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationListPreview() {
    OpenSMSTheme {
        ConversationList(isDefault = true, onConversationClick = { _, _ -> }, onRequestDefault = {})
    }
}
