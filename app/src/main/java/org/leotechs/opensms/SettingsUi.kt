package org.leotechs.opensms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class PermissionInfo(
    val permission: String,
    val name: String,
    val explanation: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyRepository = remember { KeyRepository(context) }
    var showWipeDialog by remember { mutableStateOf(false) }

    val permissions = remember {
        listOf(
            PermissionInfo(
                Manifest.permission.READ_SMS,
                "Read SMS",
                "Allows the app to read SMS messages stored on your device. This is essential for displaying your existing conversations and history."
            ),
            PermissionInfo(
                Manifest.permission.RECEIVE_SMS,
                "Receive SMS",
                "Allows the app to receive and process incoming SMS messages. Without this, you won't be notified of or see new text messages as they arrive."
            ),
            PermissionInfo(
                Manifest.permission.SEND_SMS,
                "Send SMS",
                "Allows the app to send SMS messages. This is required for you to reply to conversations and start new ones with your contacts."
            ),
            PermissionInfo(
                Manifest.permission.RECEIVE_MMS,
                "Receive MMS",
                "Allows the app to receive and process incoming media messages like photos and group texts. This ensures you can receive rich media content."
            ),
            PermissionInfo(
                Manifest.permission.READ_CONTACTS,
                "Read Contacts",
                "Allows the app to access your contact list. This is used to show names and photos instead of just phone numbers in your message list."
            ),
            PermissionInfo(
                Manifest.permission.WRITE_CONTACTS,
                "Write Contacts",
                "Allows the app to edit your contacts. This is used when you want to add a new number to your contacts or update an existing one directly."
            ),
            PermissionInfo(
                Manifest.permission.CAMERA,
                "Camera",
                "Allows the app to take photos and videos. This is required if you want to take a picture within the app to send it as an attachment."
            ),
            PermissionInfo(
                Manifest.permission.POST_NOTIFICATIONS,
                "Notifications",
                "Allows the app to show notifications for new messages. This ensures you stay updated on incoming messages even when the app is closed."
            ),
            PermissionInfo(
                Manifest.permission.CALL_PHONE,
                "Call Phone",
                "Allows the app to initiate a phone call. This is used for the shortcut that lets you quickly call a contact you are currently messaging."
            ),
            PermissionInfo(
                Manifest.permission.READ_MEDIA_IMAGES,
                "Access Photos",
                "Allows the app to access photos on your device. This is needed so you can select and send existing images from your gallery as attachments."
            ),
            PermissionInfo(
                Manifest.permission.READ_MEDIA_VIDEO,
                "Access Videos",
                "Allows the app to access videos on your device. This is required to select and share video clips from your library with your contacts."
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Wipe All Public Keys") },
                    supportingContent = { Text("Remove all stored encryption keys and reset encryption settings for all conversations.") },
                    trailingContent = {
                        Button(
                            onClick = { showWipeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Wipe")
                        }
                    }
                )
            }

            item {
                HorizontalDivider()
                Text(
                    text = "Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(permissions) { info ->
                PermissionItem(info)
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Open App Settings")
                    }
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe All Keys?") },
            text = { Text("This will permanently delete all saved public keys and disable encryption for all conversations. You will need to re-exchange keys to use encrypted messaging.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        keyRepository.clearAllKeys()
                        showWipeDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PermissionItem(info: PermissionInfo) {
    val context = LocalContext.current
    val isGranted = ContextCompat.checkSelfPermission(context, info.permission) == PackageManager.PERMISSION_GRANTED

    ListItem(
        headlineContent = { 
            Text(
                text = info.name,
                fontWeight = FontWeight.Bold
            ) 
        },
        supportingContent = { 
            Column {
                Text(info.explanation)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val successColor = Color(0xFF4CAF50)
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isGranted) successColor else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGranted) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isGranted) successColor else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
