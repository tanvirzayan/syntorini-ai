package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val isPoeticStory: Boolean = false,
    val bengaliStory: String? = null,
    val englishStory: String? = null,
    val mappedObjectsJson: String? = null,
    val sessionId: String = "session_default",
    val sessionName: String = "Active Chat"
)
