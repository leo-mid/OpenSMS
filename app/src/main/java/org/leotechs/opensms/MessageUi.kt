package org.leotechs.opensms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.leotechs.opensms.ui.theme.OpenSMSTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Patterns

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationList(
    isDefault: Boolean,
    onConversationClick: (Long, String?) -> Unit,
    onRequestDefault: () -> Unit,
    modifier: Modifier = Modifier,
    onNewConversation: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    viewModel: ConversationViewModel = viewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Top view of the conversation list
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { 
                                menuExpanded = false
                                onSettingsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") },
                            onClick = { 
                                menuExpanded = false
                                onAboutClick()
                            }
                        )
                    }
                }
            }

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

                    val conversationSwipeActions = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.toggleReadStatus(
                                    currentConversation.threadId,
                                    currentConversation.isRead
                                )
                                false
                            } else if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteConversation(conversation.threadId)
                                false
                            } else {
                                false
                            }
                        },
                        positionalThreshold = { totalDistance -> totalDistance * 0.8f }
                    )

                    SwipeToDismissBox(
                        state = conversationSwipeActions,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            when (conversationSwipeActions.dismissDirection){
                                SwipeToDismissBoxValue.StartToEnd -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha =
                                                if (conversationSwipeActions.progress > 0f) 1f else 0f
                                        }
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = if (conversation.isRead) "Mark as Unread" else "Mark as Read",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = if (conversationSwipeActions.progress > 0.4f) 1f else 0f
                                        }
                                    )
                                }
                                SwipeToDismissBoxValue.EndToStart -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            alpha =
                                                if (conversationSwipeActions.progress > 0f) 1f else 0f
                                        }
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ){
                                    Text(
                                        text = "Delete",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = if (conversationSwipeActions.progress > 0.4f) 1f else 0f
                                        }
                                    )
                                }
                                else -> {
                                    // Nothing goes here gang left internally blank
                                }
                            }
                        }
                    ) {
                        // What Actually gets displayed with the swipe controls to it
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
        timestampHandler(conversation.date, "ConvoView")
    }

    ListItem(
        modifier = Modifier.clickable { onClick() },
        // Contact Name / Phone Number
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.contactName ?: conversation.address,
                    fontWeight = if (conversation.isRead) FontWeight.Normal else FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.isBlocked) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Blocked",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (conversation.isAlwaysEncrypted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Always Encrypted",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Spacer(modifier = Modifier.size(10.dp))
                }

                // Handles the contact picture information
                if (conversation.isGroup) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Group",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else if (conversation.contactPhotoUri != null) {
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
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "AccountCircle",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        },
        trailingContent = {
            Text(text = dateString, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

// Handles how the different timestamps are displayed
private fun timestampHandler(timestamp: Long, type: String? = null): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val msgDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    
    val isSameDay = now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)
            
    val dayStr = when {
        isSameDay -> "Today"
        else -> {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = yesterday.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR)
            
            if (isYesterday) {
                "Yesterday"
            } else {
                val sixDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                if (!msgDate.before(sixDaysAgo)) {
                    SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
                } else {
                    SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(date)
                }
            }
        }
    }
    if (type == "MessageView"){
        return "$dayStr\n$timeStr"
    } else if (type == "ConvoView"){
        if ("$dayStr" == "Today"){
            return "$timeStr"
        }
        return "$dayStr"
    } else {
        return "$timeStr"
    }
}

@Composable
fun MessageDetail(
    threadId: Long,
    contactName: String?,
    onBack: () -> Unit,
    onSendSms: (String, String, Boolean) -> Boolean,
    onSendMms: (String, Uri?) -> Boolean,
    modifier: Modifier = Modifier,
    initialMessage: String? = null
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val messages = remember(threadId) { mutableStateListOf<Message>() }
    var phoneNumber by remember(threadId) { mutableStateOf("") }
    var displayName by remember(threadId) { mutableStateOf(contactName) }
    var messageText by remember(threadId, initialMessage) { mutableStateOf(initialMessage ?: "") }
    var page by remember(threadId) { mutableIntStateOf(0) }
    var canLoadMore by remember(threadId) { mutableStateOf(true) }
    var isLoading by remember(threadId) { mutableStateOf(false) }
    var loadJob by remember(threadId) { mutableStateOf<Job?>(null) }
    var lastUpdate by remember(threadId) { mutableLongStateOf(0L) }
    var isEncryptionEnabled by remember(threadId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isGroup = phoneNumber.contains(",")

    // Media Handling
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var isBlocked by remember(phoneNumber) { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }

    LaunchedEffect(phoneNumber) {
        if (!isGroup && phoneNumber.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                isBlocked = repository.isBlocked(phoneNumber)
            }
        }
    }

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

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            title = { Text(if (isBlocked) "Unblock Number" else "Block Number") },
            text = { Text(if (isBlocked) "Are you sure you want to unblock $phoneNumber?" else "Are you sure you want to block $phoneNumber? You will no longer receive messages from this number.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (isBlocked) {
                                repository.unblockNumber(phoneNumber)
                            } else {
                                repository.blockNumber(phoneNumber)
                            }
                            isBlocked = !isBlocked
                            withContext(Dispatchers.Main) {
                                showBlockDialog = false
                            }
                        }
                    }
                ) {
                    Text(if (isBlocked) "Unblock" else "Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    fun loadMessages(isRefresh: Boolean = false) {
        if (isRefresh) {
            loadJob?.cancel()
        } else if (isLoading || !canLoadMore) {
            return
        }
        
        loadJob = scope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                if (isRefresh) {
                    page = 0
                    canLoadMore = true
                    repository.clearContactCache()
                }

                val newMsgs = repository.getMessages(threadId, limit = 30, offset = page * 30)
                
                withContext(Dispatchers.Main) {
                    if (isRefresh) {
                        messages.clear()
                        messages.addAll(newMsgs)
                    } else {
                        // Loading older messages (pagination)
                        val existingIds = messages.map { it.id }.toSet()
                        val uniqueNewMsgs = newMsgs.filter { !existingIds.contains(it.id) }
                        messages.addAll(uniqueNewMsgs)
                        messages.sortByDescending { it.date }
                    }
                    
                    if (newMsgs.size < 30) {
                        canLoadMore = false
                    }
                    
                    if (!isRefresh && newMsgs.isNotEmpty()) {
                        page++
                    } else if (isRefresh && messages.isNotEmpty()) {
                        // If refreshed start back at the first page
                        page = 1 
                    }

                    // Get all addresses for the conversation if not already set
                    if (phoneNumber.isEmpty()) {
                        val addresses = repository.getAddressesForThread(threadId)
                        phoneNumber = addresses.joinToString(", ")
                    }

                    // Refresh display name
                    displayName = repository.getThreadName(threadId)

                    lastUpdate = System.currentTimeMillis()
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(threadId) {
        val keyRepo = KeyRepository(context)
        isEncryptionEnabled = keyRepo.isEncryptionEnabled(threadId)
        loadMessages(isRefresh = true)
    }

    DisposableEffect(threadId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // When content changes, refresh to get the latest messages
                loadMessages(isRefresh = true)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver("content://mms-sms/".toUri(), true, observer)
        resolver.registerContentObserver("content://sms/".toUri(), true, observer)
        resolver.registerContentObserver("content://mms/".toUri(), true, observer)
        resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        
        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    // Displays the conversation and sets up the current view for it
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val maxHeight = maxHeight / 2
        val context = LocalContext.current

        val callPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = "tel:$phoneNumber".toUri()
                }
                context.startActivity(intent)
            }
        }

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
                    text = displayName ?: phoneNumber,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )

                if (isBlocked){
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Blocked",
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                if (isEncryptionEnabled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Always Encrypted",
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Shoves the call & menu button to the right
                Spacer(modifier = Modifier.weight(1f))

                if (!isGroup){
                    IconButton(
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CALL_PHONE
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val intent = Intent(Intent.ACTION_CALL).apply {
                                    data = "tel:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            } else {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call"
                        )
                    }
                }

                Box{
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (!isGroup && contactName == null) {
                            DropdownMenuItem(
                                text = { Text("Add to Contacts") },
                                onClick = {
                                    menuExpanded = false
                                    val intent = Intent(Intent.ACTION_INSERT).apply {
                                        type = ContactsContract.Contacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Edit Contact") },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch(Dispatchers.IO) {
                                        val contactUri = repository.getContactLookupUri(phoneNumber)
                                        if (contactUri != null) {
                                            val intent = Intent(Intent.ACTION_EDIT).apply {
                                                data = contactUri
                                            }
                                            withContext(Dispatchers.Main) {
                                                context.startActivity(intent)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Share Encryption Key") },
                            onClick = {
                                menuExpanded = false
                                val myKey = CryptoUtils.getLocalPublicKeyBase64()
                                onSendSms(phoneNumber, "[KEY]$myKey", false)
                                Toast.makeText(context, "Public key sent!", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                if(isEncryptionEnabled){
                                    Text("Unencrypt Conversation")
                                } else
                                    Text("Encrypt Conversation")
                            },
                            onClick = {
                                val newState = !isEncryptionEnabled
                                isEncryptionEnabled = newState
                                KeyRepository(context).setEncryptionEnabled(threadId, newState)
                                menuExpanded = false
                            }
                        )
                        if(!isGroup){
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock Number" else "Block Number") },
                                onClick = {
                                    menuExpanded = false
                                    showBlockDialog = true
                                }
                            )
                        } else {
                            Text(
                                "Members",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )

                            HorizontalDivider()

                            for (contact in repository.getContactsForThread(threadId)) {
                                val isKnown = contact.name != contact.number
                                DropdownMenuItem(
                                    text = { Text(contact.name) },
                                    trailingIcon = {
                                        if (isKnown && contact.photoUri != null) {
                                            AsyncImage(
                                                model = contact.photoUri,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                if (isKnown) Icons.Default.AccountCircle else Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (isKnown) {
                                            scope.launch(Dispatchers.IO) {
                                                val contactUri = repository.getContactLookupUri(contact.number)
                                                if (contactUri != null) {
                                                    val intent = Intent(Intent.ACTION_EDIT).apply {
                                                        data = contactUri
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            }
                                        } else {
                                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                                type = ContactsContract.Contacts.CONTENT_TYPE
                                                putExtra(ContactsContract.Intents.Insert.PHONE, contact.number)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val listState = rememberLazyListState()
            val imeBottom = WindowInsets.ime.getBottom(density)
            val swipeOffset = remember { Animatable(0f) }

            // Auto-scroll to bottom (index 0 in reverse layout) when list updates or keyboard opens
            LaunchedEffect(lastUpdate, imeBottom) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }

            // Load more when reaching the end (top of the list in reverse layout)
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collect { index ->
                        if (messages.isNotEmpty() && 
                            index + listState.layoutInfo.visibleItemsInfo.size >= messages.size - 5) {
                            loadMessages()
                        }
                    }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                itemsIndexed(messages, key = { _, it -> it.id }) { index, message ->
                    val nextMessage = if (index + 1 < messages.size) messages[index + 1] else null
                    
                    // Only shows the timestamp if its been more than an hour between the last messages with the same person
                    val showTimestamp = nextMessage == null || (message.date - nextMessage.date > 60 * 60 * 1000)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showTimestamp) {
                            Text(
                                text = timestampHandler(message.date, "MessageView"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .animateItem()
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            // Revealed timestamp
                            Text(
                                text = timestampHandler(message.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .alpha(if (swipeOffset.value < 0f) (-swipeOffset.value / 200f).coerceIn(0f, 1f) else 0f)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                scope.launch { swipeOffset.animateTo(0f) }
                                            },
                                            onDragCancel = {
                                                scope.launch { swipeOffset.animateTo(0f) }
                                            },
                                            onHorizontalDrag = { _, dragAmount ->
                                                scope.launch {
                                                    val newOffset = (swipeOffset.value + dragAmount).coerceIn(-200f, 0f)
                                                    swipeOffset.snapTo(newOffset)
                                                }
                                            }
                                        )
                                    }
                                    .animateItem()
                            ) {
                                MessageItem(message, isGroup)
                            }
                        }
                    }
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
                        if (onSendSms(phoneNumber, messageText, isEncryptionEnabled)) {
                            messageText = ""
                            loadMessages(isRefresh = true)
                        }
                    }) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
fun SmartLinkText(
    text: String,
    isSent: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val uriHandler = LocalUriHandler.current
    val urlPattern = Patterns.WEB_URL
    
    val annotatedString = buildAnnotatedString {
        val matcher = urlPattern.matcher(text)
        var lastIndex = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            
            append(text.substring(lastIndex, start))
            
            val fullUrl = matcher.group() ?: ""
            val destinationUrl = if (!fullUrl.startsWith("http") && !fullUrl.startsWith("ftp")) {
                "http://$fullUrl"
            } else {
                fullUrl
            }
            
            val uri = try { Uri.parse(destinationUrl) } catch (_: Exception) { null }
            val host = uri?.host ?: ""
            val path = uri?.path?.split("/")?.filter { it.isNotEmpty() }?.firstOrNull() ?: ""
            
            val displayUrl = if (host.isNotEmpty()) {
                val shortHost = host.removePrefix("www.")
                if (path.isNotEmpty() && path.length < 20) {
                    "$shortHost/$path"
                } else if (path.isNotEmpty()) {
                    "$shortHost/..."
                } else {
                    shortHost
                }
            } else {
                fullUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            }
            
            val linkColor = if (isSent) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.primary
            }
            
            pushStringAnnotation(tag = "URL", annotation = destinationUrl)
            withStyle(style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Bold
            )) {
                append(displayUrl)
            }
            pop()
            lastIndex = end
        }
        append(text.substring(lastIndex))
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = color,
            textAlign = if (isSent) TextAlign.End else TextAlign.Start
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        Log.e("SmartLinkText", "Failed to open URI: ${annotation.item}", e)
                    }
                }
        }
    )
}

// Displays all the messages in a conversation
@Composable
fun MessageItem(message: Message, isGroup: Boolean = false) {
    val isSent = message.type == 2
    var showFullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }

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
            dismissButton = {
                TextButton(
                    onClick = {
                        val success = MmsUtils.saveMediaToGallery(
                            context,
                            message.mediaUri,
                            message.mediaContentType
                        )
                        if (success) {
                            Toast.makeText(context, "Saved to gallery!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to save.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save to Device")
                }
            },
            text = {
                if (message.mediaContentType?.startsWith("video/") == true) {
                    VideoPlayer(
                        uri = message.mediaUri,
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
        Column(horizontalAlignment = if (isSent) Alignment.End else Alignment.Start) {
            if (isGroup && !isSent && message.senderAddress != null) {
                val senderInfo = remember(message.senderAddress) {
                    repository.getContactInfo(message.senderAddress)
                }
                Text(
                    text = senderInfo.first ?: message.senderAddress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            Box(
                // Standard message box with no images and stuff
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Checks if the message has an attachment and handles it to display it in the correct way
                Column {
                    if (message.isMms && message.mediaUri != null) {
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
                        SmartLinkText(
                            text = message.body,
                            isSent = isSent,
                            color = if (isSent) MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
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
            onRequestDefault = {},
            onSettingsClick = {},
            onAboutClick = {}
        )
    }
}
