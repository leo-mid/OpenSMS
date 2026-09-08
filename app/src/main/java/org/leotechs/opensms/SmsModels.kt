package org.leotechs.opensms

import android.net.Uri

data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val contactName: String?,
    val contactPhotoUri: String?,
    val isEncrypted: Boolean,
    val isRead: Boolean = true,
    val isGroup: Boolean = false,
    val isBlocked: Boolean = false,
    val addresses: List<String> = emptyList()
)

data class Message(
    val id: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 for inbox, 2 for sent
    val isEncrypted: Boolean,
    val isMms: Boolean = false,
    val mediaUri: Uri? = null,
    val mediaContentType: String? = null,
    val senderAddress: String? = null // Address of the actual sender (useful for group chats)
)

data class Contact(
    val name: String,
    val number: String,
    val photoUri: String? = null
)
