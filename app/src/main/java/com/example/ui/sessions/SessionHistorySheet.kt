package com.example.ui.sessions

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ChatMessageEntity
import com.example.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

data class SessionItemInfo(
    val sessionId: String,
    val sessionName: String,
    val timestamp: Long,
    val snippet: String,
    val messageCount: Int
)

@Composable
fun SessionHistorySheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val allMessagesHistory by viewModel.allMessagesHistory.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val activeSessionName by viewModel.currentSessionName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var newSessionNameInput by remember { mutableStateOf("") }
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Sessions, 1 = Raw turns in active session

    // Projects the flat message table into unique sessions for easy navigation
    val groupedSessions = remember(allMessagesHistory) {
        allMessagesHistory.groupBy { it.sessionId }.map { (id, msgs) ->
            val firstMsg = msgs.firstOrNull()
            val latestMsg = msgs.lastOrNull()
            val name = firstMsg?.sessionName ?: "Untitled Conversation"
            val timestamp = latestMsg?.timestamp ?: System.currentTimeMillis()
            
            // Clean snippet
            val rawSnippet = latestMsg?.text ?: ""
            val snippet = if (rawSnippet.startsWith("A poetic reflection was woven")) {
                latestMsg?.bengaliStory?.take(60)?.plus("...") ?: "Poetic Scan"
            } else {
                rawSnippet
            }
            
            SessionItemInfo(
                sessionId = id,
                sessionName = name,
                timestamp = timestamp,
                snippet = snippet,
                messageCount = msgs.size
            )
        }.sortedByDescending { it.timestamp }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131314).copy(alpha = 0.95f))
            .testTag("session_history_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            // Header Action Pane
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Conversation Ledger",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E1F20), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close history drawer",
                        tint = Color(0xFFC4C7C5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub Tab select
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1F20), shape = RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                // Sessions Tab Button
                Button(
                    onClick = { activeSubTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeSubTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (activeSubTab == 0) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4C7C5)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Chat Threads (${groupedSessions.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Turns Tab Button
                Button(
                    onClick = { activeSubTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activeSubTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (activeSubTab == 1) MaterialTheme.colorScheme.onPrimary else Color(0xFFC4C7C5)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Current Session Turns", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activeSubTab == 0) {
                // Dynamic Input to create/save named sessions
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F20)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    border = BorderStroke(1.dp, Color(0xFF333537))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "START NEW CHAT THREAD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newSessionNameInput,
                                onValueChange = { newSessionNameInput = it },
                                placeholder = {
                                    Text(
                                        "Enter topic/name...",
                                        fontSize = 13.sp,
                                        color = Color(0xFF8E918F)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("session_name_input"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF333537),
                                    focusedContainerColor = Color(0xFF131314),
                                    unfocusedContainerColor = Color(0xFF131314)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val name = newSessionNameInput.ifBlank {
                                        "Thread ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}"
                                    }
                                    viewModel.createNewSession(name)
                                    newSessionNameInput = ""
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                    .testTag("create_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Session",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Scrollable Sessions Timeline list
                Text(
                    text = "ACTIVE & HISTORY THREADS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF8E918F),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (groupedSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Your conversation threads will appear here securely.",
                            fontSize = 13.sp,
                            color = Color(0xFF8E918F),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(groupedSessions) { session ->
                            val isActive = session.sessionId == activeSessionId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSession(session.sessionId, session.sessionName)
                                    }
                                    .testTag("session_item_${session.sessionId}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isActive) Color(0xFF1a273b) else Color(0xFF1E1F20),
                                border = BorderStroke(
                                    1.dp,
                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color(0xFF333537)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = session.sessionName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                            shape = CircleShape
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        "Active",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (session.snippet.isNotBlank()) session.snippet else "No content inside thread.",
                                            fontSize = 12.sp,
                                            color = Color(0xFFC4C7C5),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontStyle = if (session.snippet.isBlank()) FontStyle.Italic else FontStyle.Normal
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.alpha(0.6f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                                contentDescription = null,
                                                tint = Color(0xFF8E918F),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${session.messageCount} conversation turns",
                                                fontSize = 10.sp,
                                                color = Color(0xFF8E918F)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "•",
                                                fontSize = 10.sp,
                                                color = Color(0xFF8E918F)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val date = Date(session.timestamp)
                                            val prettyDate = SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault()).format(date)
                                            Text(
                                                text = prettyDate,
                                                fontSize = 10.sp,
                                                color = Color(0xFF8E918F)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Session delete button
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteSession(session.sessionId)
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.Black.copy(alpha = 0.2f), shape = CircleShape)
                                            .testTag("delete_session_${session.sessionId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Session",
                                            tint = Color(0xFFD96570),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Focus on the specific Turns inside the current Active Session
                val activeSessionConversations by viewModel.chatMessages.collectAsStateWithLifecycle()

                Text(
                    text = "ACTIVE FOCUS: ${activeSessionName.uppercase()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (activeSessionConversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No conversation turns generated in this session yet.\nSend some messages in the chat tab to record turns.",
                            fontSize = 13.sp,
                            color = Color(0xFF8E918F),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(activeSessionConversations) { message ->
                            val isUser = message.role == "user"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("turn_item_${message.id}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isUser) Color(0xFF242526) else Color(0xFF1E1F20),
                                border = BorderStroke(1.dp, Color(0xFF333537))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                        shape = CircleShape
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isUser) "YOU" else "AI",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            val prettyTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(message.timestamp))
                                            Text(
                                                text = prettyTime,
                                                fontSize = 10.sp,
                                                color = Color(0xFF8E918F)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Copy text action
                                            IconButton(
                                                onClick = {
                                                    val rawText = if (message.isPoeticStory) {
                                                        "${message.bengaliStory}\n\n${message.englishStory}"
                                                    } else {
                                                        message.text
                                                    }
                                                    clipboard.setText(AnnotatedString(rawText))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Content",
                                                    tint = Color(0xFFC4C7C5),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Delete turn action
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteMessageById(message.id)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Turn",
                                                    tint = Color(0xFFD96570),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (message.isPoeticStory) {
                                        Text(
                                            text = message.bengaliStory ?: "",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = message.englishStory ?: "",
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = Color(0xFFC4C7C5),
                                            lineHeight = 16.sp
                                        )
                                    } else {
                                        Text(
                                            text = message.text,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
