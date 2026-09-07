package org.leotechs.opensms

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import org.leotechs.opensms.ui.theme.OpenSMSTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationList(
    isDefault: Boolean,
    onConversationClick: (Long, String?) -> Unit,
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier,
    onNewConversation: () -> Unit = {},
    viewModel: ConversationViewModel = viewModel()
) {
    val conversations by viewModel.conversations.collectAsState()

    Box(modifier = modifier) {
        // Top view of the conversation list
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
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

            // Displays the conversations
            LazyColumn(modifier = Modifier.weight(1f)) {
                // Fetches all the conversations and starts to list them
                items(conversations, key = { it.threadId }) { conversation ->
                    val currentConversation by rememberUpdatedState(conversation)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.toggleReadStatus(
                                    currentConversation.threadId,
                                    currentConversation.isRead
                                )
                                false
                            } else {
                                false
                            }
                        },
                        positionalThreshold = { totalDistance -> totalDistance * 0.8f }
                    )

                    val deleteState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteConversation(conversation.threadId)
                                false
                            } else {
                                false
                            }
                        }
                    )

                    // Swipe to delete a conversation - Right to left
                    SwipeToDismissBox(
                        state = deleteState,
                        enableDismissFromStartToEnd =  false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (dismissState.progress > 0f) 1f else 0f
                                    }
                                    .background(Color(0xFF8C1212))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ){
                                Text(
                                    text = "Delete",
                                    color = Color(0xFFFF7A7A),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (dismissState.progress > 0.4f) 1f else 0f
                                    }
                                )
                            }
                        }
                    ) {
                        ConversationItem(conversation) {
                            onConversationClick(conversation.threadId, conversation.contactName)
                        }
                    }

                    // Handles the marking as read/unread swipe controls
                    SwipeToDismissBox(
                        state = dismissState,
                        // Left to right
                        enableDismissFromStartToEnd = true,
                        // Right to Left
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            // Use graphicsLayer for alpha to avoid recomposition
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (dismissState.progress > 0f) 1f else 0f
                                    }
                                    .background(Color(0xff3b719f))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (conversation.isRead) "Mark as Unread" else "Mark as Read",
                                    color = Color(0xFFA4D5FF),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (dismissState.progress > 0.4f) 1f else 0f
                                    }
                                )
                            }
                        }
                    ) {
                        // What Actually gets displayed with the tap controls with it
                        ConversationItem(conversation) {
                            onConversationClick(conversation.threadId, conversation.contactName)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        // Creates a new conversation button
        FloatingActionButton(
            onClick = onNewConversation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Message")
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
        // Contact Name / Phone Number
        headlineContent = {
            Text(
                text = conversation.contactName ?: conversation.address,
                fontWeight = if (conversation.isRead) FontWeight.Normal else FontWeight.Bold
            )
        },
        // Message Preview
        supportingContent = {
            Text(
                text = conversation.snippet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (conversation.isRead) FontWeight.Normal else FontWeight.Medium
            )
        },
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Unread dot
                if (!conversation.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF007AFF))
                    )
                } else {
                    Spacer(modifier = Modifier.size(10.dp))
                }

                // Handles the contact picture information
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
                        if (conversation.contactName != null) {
                            Text(
                                text = conversation.contactName.take(1).uppercase(),
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "?",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Text(text = dateString, color = Color.Gray)
        }
    )
}

// Controls how the date is formated for the conversation
// Ex: Yesterday, Weekdays, 6 days ago + mm/dd/YY
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
    onSendMms: (String, Uri) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val messages = remember { mutableStateListOf<Message>() }
    var phoneNumber by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }

    // Media Handling
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    // Controls the camera actions
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            onSendMms(phoneNumber, tempImageUri!!)
        }
    }

    // Controls the gallery actions
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendMms(phoneNumber, uri)
        }
    }

    // Handles camera image creation
    fun createTempUri(): Uri {
        val tempFile = File.createTempFile("captured_image", ".jpg", context.externalCacheDir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    BackHandler(onBack = onBack)

    fun refreshMessages() {
        val msgs = repository.getMessages(threadId)
        messages.clear()
        messages.addAll(msgs)
        if (msgs.isNotEmpty()) {
            phoneNumber = msgs.last().address
        }
    }

    LaunchedEffect(threadId) {
        refreshMessages()
    }

    DisposableEffect(threadId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshMessages()
            }
        }
        context.contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"),
            true,
            observer
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // Displays the conversation and sets up the current view for it
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxHeight = maxHeight / 2

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Layout of the top part of the conversation view
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = contactName ?: phoneNumber,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            val listState = rememberLazyListState()
            
            // Auto-scroll when messages arrive
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            // Auto-scroll when keyboard height changes
            val imeBottom = WindowInsets.ime.getBottom(density)
            LaunchedEffect(imeBottom) {
                if (imeBottom > 0 && messages.isNotEmpty()) {
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

            // Send bar information
            // Creates the icon for the media, text field for the messages, and the send button

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
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = maxHeight),
                    placeholder = { Text("Message") },
                    maxLines = 100
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(onClick = {
                        if (onSendSms(phoneNumber, messageText, false)) {
                            messageText = ""
                        }
                    }) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

// Displays all the messages in a conversation
@Composable
fun MessageItem(message: Message) {
    val isSent = message.type == 2
    var showFullScreen by remember { mutableStateOf(false) }

    // Creates the view to see media attachments in the conversation
    // Plays the videos in this box as well
    if (showFullScreen && message.mediaUri != null) {
        AlertDialog(
            onDismissRequest = { showFullScreen = false },
            confirmButton = {
                TextButton(onClick = { showFullScreen = false }) {
                    Text("Close")
                }
            },
            text = {
                if (message.mediaContentType?.startsWith("video/") == true) {
                    VideoPlayer(
                        uri = message.mediaUri!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                } else {
                    AsyncImage(
                        model = message.mediaUri,
                        contentDescription = "Full Screen Media",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )
    }

    // Displays the messages in the conversation

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Box(
            // Standard message box with no images and stuff
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSent) Color(0xFF007AFF) else Color(0xFFE9E9EB))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Checks if the message has an attachment and handles it to display it in the correct way
            Column {
                if (message.isMms && message.mediaUri != null) {
                    val context = LocalContext.current
                    val isVideo = message.mediaContentType?.startsWith("video/") == true
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { showFullScreen = true }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(message.mediaUri)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = "MMS Content",
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .heightIn(max = 250.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                            onError = {
                                Log.e("MessageUi", "Coil failed to load ${message.mediaUri}: ${it.result.throwable}")
                            }
                        )
                        if (isVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    if (message.body.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                if (message.body.isNotEmpty()) {
                    Text(
                        text = message.body,
                        color = if (isSent) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConversationListPreview() {
    OpenSMSTheme {
        ConversationList(
            isDefault = true,
            onConversationClick = { _: Long, _: String? -> },
            onRequestDefault = {}
        )
    }
}
