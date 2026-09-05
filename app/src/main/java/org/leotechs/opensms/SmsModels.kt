package org.leotechs.opensms

data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val contactName: String?,
    val contactPhotoUri: String?,
    val isEncrypted: Boolean
)

data class Message(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 for inbox, 2 for sent
    val isEncrypted: Boolean
)
