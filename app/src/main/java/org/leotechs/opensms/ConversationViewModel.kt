package org.leotechs.opensms

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    init {
        val resolver = application.contentResolver
        resolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
        resolver.registerContentObserver(Uri.parse("content://sms/"), true, observer)
        resolver.registerContentObserver(Uri.parse("content://mms/"), true, observer)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val newConversations = repository.getConversations()
                _conversations.value = newConversations
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun toggleReadStatus(threadId: Long, currentIsRead: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("ConversationViewModel", "Toggling read status for thread $threadId. Current: $currentIsRead")
            if (currentIsRead) {
                repository.markAsUnread(threadId)
            } else {
                repository.markAsRead(threadId)
            }
            Log.d("ConversationViewModel", "Toggle complete, refreshing...")
            refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
