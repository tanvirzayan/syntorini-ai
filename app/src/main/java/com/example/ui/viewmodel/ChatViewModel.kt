package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ChatMessageEntity
import com.example.data.network.GeminiClient
import com.example.data.network.MappedObject
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    private val TAG = "ChatViewModel"

    private val _currentSessionId = MutableStateFlow("session_default")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _currentSessionName = MutableStateFlow("Default Session")
    val currentSessionName: StateFlow<String> = _currentSessionName.asStateFlow()

    // Load active session items reactively from repository
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _currentSessionId
        .flatMapLatest { sessionId ->
            repository.getMessagesBySession(sessionId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Load all historical items reactively to map existing sessions
    val allMessagesHistory: StateFlow<List<ChatMessageEntity>> = repository.allMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isGeneratingText = MutableStateFlow(false)
    val isGeneratingText: StateFlow<Boolean> = _isGeneratingText.asStateFlow()

    private val _isMappingImage = MutableStateFlow(false)
    val isMappingImage: StateFlow<Boolean> = _isMappingImage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Set active active session to navigate
     */
    fun selectSession(sessionId: String, sessionName: String) {
        _currentSessionId.value = sessionId
        _currentSessionName.value = sessionName
    }

    /**
     * Start a fresh conversation session with a unique ID
     */
    fun createNewSession(name: String) {
        val newId = "session_" + java.util.UUID.randomUUID().toString().take(8)
        _currentSessionId.value = newId
        _currentSessionName.value = name
    }

    /**
     * Safely delete a session and fallback to default if current was deleted
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                // reset to default session safely
                _currentSessionId.value = "session_default"
                _currentSessionName.value = "Default Session"
            }
        }
    }

    /**
     * Delete user/ai message turn specifically from state
     */
    fun deleteMessageById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessageById(id)
        }
    }

    /**
     * Standard Chat Action
     */
    fun sendMessage(promptText: String) {
        if (promptText.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isGeneratingText.value = true
            _errorMessage.value = null

            // 1. Save user prompt locally
            val userMsg = ChatMessageEntity(
                role = "user",
                text = promptText,
                sessionId = _currentSessionId.value,
                sessionName = _currentSessionName.value
            )
            repository.insertMessage(userMsg)

            // 2. Fetch conversation history for context (ignoring poetic stories or large images to prevent context limit)
            val currentHistory = chatMessages.value
                .filter { !it.isPoeticStory && it.imageUri == null }
                .takeLast(10) // Only send the last 10 text exchanges
                .map { Pair(it.role, it.text) }

            // 3. Request model response
            val responseText = GeminiClient.sendChatRequest(promptText, currentHistory)

            // 4. Save model response locally
            val modelMsg = ChatMessageEntity(
                role = "model",
                text = responseText,
                sessionId = _currentSessionId.value,
                sessionName = _currentSessionName.value
            )
            repository.insertMessage(modelMsg)

            _isGeneratingText.value = false
        }
    }

    /**
     * Subconscious Lens Image Mapping Action
     */
    fun mapImageMetaphors(context: Context, imageUri: Uri, emotionalNotes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isMappingImage.value = true
            _errorMessage.value = null

            // 1. First, save an entry for the User's upload in history
            val userUploadMsg = ChatMessageEntity(
                role = "user",
                text = if (emotionalNotes.isNotBlank()) "Scanned surroundings with notes: '$emotionalNotes'" else "Scanned surroundings with Subconscious Lens.",
                imageUri = imageUri.toString(),
                sessionId = _currentSessionId.value,
                sessionName = _currentSessionName.value
            )
            repository.insertMessage(userUploadMsg)

            // 2. Trigger the Subconscious Lens model
            val result = GeminiClient.analyzeImageForStory(context, imageUri, emotionalNotes)

            if (result.error != null) {
                _errorMessage.value = result.error
                // Save model error msg so history feels complete
                val errorMsg = ChatMessageEntity(
                    role = "model",
                    text = "My subconscious scanning encountered an offset: ${result.error}",
                    sessionId = _currentSessionId.value,
                    sessionName = _currentSessionName.value
                )
                repository.insertMessage(errorMsg)
            } else {
                // 3. Save the gorgeous poetic story results as a specialized item
                val serializedObjects = serializeMappedObjects(result.objects)
                
                val modelPoeticMsg = ChatMessageEntity(
                    role = "model",
                    text = "A poetic reflection was woven is response...",
                    isPoeticStory = true,
                    bengaliStory = result.storyBengali,
                    englishStory = result.storyEnglish,
                    mappedObjectsJson = serializedObjects,
                    imageUri = imageUri.toString(), // attach reference image
                    sessionId = _currentSessionId.value,
                    sessionName = _currentSessionName.value
                )
                repository.insertMessage(modelPoeticMsg)
            }

            _isMappingImage.value = false
        }
    }

    /**
     * Database Clear History
     */
    fun clearChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllMessages()
            // Reset to clean default session
            _currentSessionId.value = "session_default"
            _currentSessionName.value = "Default Session"
        }
    }

    /**
     * Save Voice Transcript to history database
     */
    fun saveVoiceTranscript(userSpeech: String, botSpeech: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val userMsg = ChatMessageEntity(
                role = "user",
                text = userSpeech,
                sessionId = _currentSessionId.value,
                sessionName = _currentSessionName.value
            )
            repository.insertMessage(userMsg)

            val modelMsg = ChatMessageEntity(
                role = "model",
                text = botSpeech,
                sessionId = _currentSessionId.value,
                sessionName = _currentSessionName.value
            )
            repository.insertMessage(modelMsg)
        }
    }

    // --- JSON Helpers ---
    private fun serializeMappedObjects(objects: List<MappedObject>): String {
        val array = JSONArray()
        for (obj in objects) {
            val o = JSONObject()
            o.put("name", obj.name)
            o.put("metaphor", obj.metaphor)
            array.put(o)
        }
        return array.toString()
    }
}

class ChatViewModelFactory(private val repository: ChatRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
