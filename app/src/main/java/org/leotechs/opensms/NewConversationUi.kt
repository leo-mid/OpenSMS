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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onMessageSent: (Long, String) -> Unit,
    onSendSms: (String, String, Boolean) -> Boolean,
    onSendMms: (String, Uri?) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    var recipientInput by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    val selectedRecipients = remember { mutableStateListOf<Contact>() }

    // Media Handling
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val sendAction = { uri: Uri?, text: String? ->
        val numbers = selectedRecipients.map { it.number }.toMutableList()
        if (recipientInput.isNotBlank() && !numbers.contains(recipientInput)) {
            numbers.add(recipientInput)
        }
        
        if (numbers.isNotEmpty()) {
            val targetNumbers = numbers.joinToString(", ")
            val success = if (uri != null) {
                onSendMms(targetNumbers, uri)
            } else if (text != null) {
                onSendSms(targetNumbers, text, false)
            } else false

            if (success) {
                val threadId = repository.getOrCreateThreadId(targetNumbers)
                onMessageSent(threadId, targetNumbers)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            sendAction(tempImageUri, null)
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            sendAction(uri, null)
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

    LaunchedEffect(recipientInput) {
        contacts = repository.searchContacts(recipientInput)
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
                // Selected people removal
                if (selectedRecipients.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedRecipients.forEach { contact ->
                            InputChip(
                                selected = true,
                                onClick = { selectedRecipients.remove(contact) },
                                label = { Text(contact.name) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = recipientInput,
                    onValueChange = {
                        recipientInput = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("To") },
                    placeholder = { Text("Type name or number") },
                    singleLine = true,
                    trailingIcon = {
                        if (recipientInput.isNotEmpty()) {
                            IconButton(onClick = { recipientInput = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )

                if (contacts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                            items(contacts) { contact ->
                                ContactItem(contact) {
                                    if (!selectedRecipients.any { it.number == contact.number }) {
                                        selectedRecipients.add(contact)
                                    }
                                    recipientInput = ""
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
                    if (messageText.isNotBlank()) {
                        sendAction(null, messageText)
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
