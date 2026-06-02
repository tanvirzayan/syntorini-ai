package com.example.data.repository

import com.example.data.database.ChatMessageDao
import com.example.data.database.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {
    val allMessages: Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessages()

    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesBySession(sessionId)
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long {
        return chatMessageDao.insertMessage(message)
    }

    suspend fun deleteMessageById(id: Int) {
        chatMessageDao.deleteMessageById(id)
    }

    suspend fun deleteSession(sessionId: String) {
        chatMessageDao.deleteSession(sessionId)
    }

    suspend fun clearAllMessages() {
        chatMessageDao.clearAllMessages()
    }
}
