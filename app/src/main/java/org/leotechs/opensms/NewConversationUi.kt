package org.leotechs.opensms

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onMessageSent: (Long, String) -> Unit,
    onSendSms: (String, String, Boolean) -> Boolean,
    onSendMms: (String, Uri) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    var recipient by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }

    // Media Handling
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            val targetNumber = selectedContact?.number ?: recipient
            if (targetNumber.isNotBlank() && onSendMms(targetNumber, tempImageUri!!)) {
                val threadId = repository.getOrCreateThreadId(targetNumber)
                onMessageSent(threadId, targetNumber)
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val targetNumber = selectedContact?.number ?: recipient
            if (targetNumber.isNotBlank() && onSendMms(targetNumber, uri)) {
                val threadId = repository.getOrCreateThreadId(targetNumber)
                onMessageSent(threadId, targetNumber)
            }
        }
    }

    fun createTempUri(): Uri {
        val tempFile = File.createTempFile("captured_image", ".jpg", context.externalCacheDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    BackHandler(onBack = onBack)

    LaunchedEffect(recipient) {
        if (selectedContact == null) {
            contacts = repository.searchContacts(recipient)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().imePadding()) {
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                OutlinedTextField(
                    value = if (selectedContact != null) selectedContact!!.name else recipient,
                    onValueChange = {
                        recipient = it
                        selectedContact = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("To") },
                    placeholder = { Text("Type name or number") },
                    singleLine = true,
                    trailingIcon = {
                        if (selectedContact != null || recipient.isNotEmpty()) {
                            IconButton(onClick = { selectedContact = null; recipient = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )

                if (selectedContact == null && contacts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(contacts) { contact ->
                                ContactItem(contact) {
                                    selectedContact = contact
                                    contacts = emptyList()
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Message Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var menuExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add attachment")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Camera") },
                            onClick = {
                                menuExpanded = false
                                tempImageUri = createTempUri()
                                cameraLauncher.launch(tempImageUri!!)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Photos") },
                            onClick = {
                                menuExpanded = false
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }
                        )
                    }
                }

                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 10
                )

                Button(onClick = {
                    val targetNumber = selectedContact?.number ?: recipient
                    if (targetNumber.isNotBlank() && messageText.isNotBlank()) {
                        if (onSendSms(targetNumber, messageText, false)) {
                            val threadId = repository.getOrCreateThreadId(targetNumber)
                            onMessageSent(threadId, targetNumber)
                        }
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(contact.number) },
        leadingContent = {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(contact.name.take(1).uppercase(), color = Color.White)
                }
            }
        }
    )
}
