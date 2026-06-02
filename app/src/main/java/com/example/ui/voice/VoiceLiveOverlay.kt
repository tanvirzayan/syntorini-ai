package com.example.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.ChatMessageEntity
import com.example.data.network.GeminiClient
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceLiveOverlay(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Permission State
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required for Live Voice Mode", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    // Trigger permission requests if not granted initially
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasPermission) {
        VoiceLiveSessionContent(
            viewModel = viewModel,
            onDismiss = onDismiss,
            context = context
        )
    } else {
        // Simple permission instructions overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131314)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Allow microphone usage?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Syntorini needs microphone access to enable real-time live conversations.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFC4C7C5)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8AB4F8))
                ) {
                    Text("Grant Permission", color = Color(0xFF131314))
                }
            }
        }
    }
}

@Composable
fun VoiceLiveSessionContent(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

    // Conversational state variables
    var currentVoiceStateText by remember { mutableStateOf("Syntorini is ready. Speak now!") }
    var userTranscription by remember { mutableStateOf("") }
    var systemOutputSpeech by remember { mutableStateOf("") }
    
    var isListeningState by remember { mutableStateOf(false) }
    var isSpeakingState by remember { mutableStateOf(false) }
    var isThinkingState by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    // Sound wave frequency animation multipliers
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceGlow")
    
    // Scale pulse for orb
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulse"
    )

    // Spin animation for background halo
    val haloRotateState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Audio stream helper classes
    var voiceManager: VoiceManager? by remember { mutableStateOf(null) }

    // Initialize VoiceManager once and clean up on exit
    LaunchedEffect(Unit) {
        val manager = VoiceManager(
            context = context,
            onSpeechRecognized = { text ->
                if (text.isNotBlank()) {
                    userTranscription = text
                    isListeningState = false
                    isThinkingState = true
                    currentVoiceStateText = "Thinking..."
                    
                    scope.launch {
                        try {
                            // Extract basic chat contexts
                            val contextHistory = chatMessages
                                .filter { !it.isPoeticStory && it.imageUri == null }
                                .takeLast(6)
                                .map { Pair(it.role, it.text) }

                            // Prompt engineering to keep responses vocal, delightful, and natural
                            val naturalPrompt = "$text (Make your response brief and vocal: 1-3 highly conversational sentences maximum. Avoid any markdown codeblocks or technical logs. Output either warm Bengali, natural Banglish, or elegant English as preferred)."
                            
                            val response = GeminiClient.sendChatRequest(naturalPrompt, contextHistory)
                            
                            systemOutputSpeech = response
                            isThinkingState = false
                            
                            // Save to core SQLite Room database
                            viewModel.saveVoiceTranscript(text, response)
                            
                            // Speak out loud!
                            if (!isMuted) {
                                voiceManager?.speak(response)
                            } else {
                                currentVoiceStateText = "Syntorini response ready (Muted)"
                            }
                        } catch (e: Exception) {
                            Log.e("VoiceOverlay", "Response processing failed", e)
                            currentVoiceStateText = "Connection paused"
                            isThinkingState = false
                        }
                    }
                }
            },
            onSpeechListeningStateChanged = { listening ->
                isListeningState = listening
                if (listening) {
                    currentVoiceStateText = "Listening..."
                    isSpeakingState = false
                    isThinkingState = false
                    systemOutputSpeech = ""
                } else if (!isThinkingState && !isSpeakingState) {
                    currentVoiceStateText = "Tap mic to talk"
                }
            },
            onTtsSpeakingStateChanged = { speaking ->
                isSpeakingState = speaking
                if (speaking) {
                    currentVoiceStateText = "Speaking..."
                    isListeningState = false
                    isThinkingState = false
                } else if (!isListeningState && !isThinkingState) {
                    currentVoiceStateText = "Tap mic to talk"
                }
            },
            onErrorOccurred = { error ->
                Log.e("VoiceOverlay", "Speech subsystem error: $error")
            }
        )
        voiceManager = manager
        
        // Greet the user via synthetic speaker on entrance
        delay(600)
        manager.speak("Hello! I am Syntorini. Speak or ask me anything. আমি আপনার কথা শুনতে প্রস্তুত।")
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager?.apply {
                stopSpeaking()
                stopListening()
                destroy()
            }
        }
    }

    // Dynamic coloring based on current communication state
    val orbColorGradients = when {
        isThinkingState -> listOf(Color(0xFF9B72CB), Color(0xFFD96570)) // Violet with Coral
        isListeningState -> listOf(Color(0xFF4285F4), Color(0xFF8AB4F8)) // Blue tones
        isSpeakingState -> listOf(Color(0xFFD96570), Color(0xFFF9AB00)) // Warm coral-orange tones
        else -> listOf(Color(0xFF4285F4), Color(0xFF9B72CB)) // Default sleep purple-blue
    }

    val stateBadgeText = when {
        isThinkingState -> "SYNTHESIZING RESPONSE"
        isListeningState -> "LISTENING LIVE"
        isSpeakingState -> "SPEAKING OUT LOUD"
        isMuted -> "SPEAKER MUTED"
        else -> "SECURE VOICE CALL"
    }

    val stateBadgeColor = when {
        isThinkingState -> Color(0xFF9B72CB)
        isListeningState -> Color(0xFF8AB4F8)
        isSpeakingState -> Color(0xFFD96570)
        else -> Color(0xFF8E918F)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131314))
            .testTag("voice_chat_overlay")
    ) {
        // Cosmic Background star halos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = haloRotateState
                }
        ) {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.Center)
                    .alpha(0.04f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF8AB4F8), Color.Transparent)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Syntorini Live Voice",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(stateBadgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stateBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = stateBadgeColor
                        )
                    }
                }

                IconButton(
                    onClick = { onDismiss() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1E1F20), shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Call",
                        tint = Color(0xFFC4C7C5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Central Pulsing Core
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Secondary background breathing ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(pulseScale * 1.15f)
                        .background(
                            Brush.sweepGradient(orbColorGradients),
                            shape = CircleShape
                        )
                        .alpha(0.08f)
                )

                // Primary animated energy ball
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.linearGradient(
                                colors = orbColorGradients
                            ),
                            shape = CircleShape
                        )
                        .clickable {
                            if (!isListeningState) {
                                voiceManager?.startListening()
                            } else {
                                voiceManager?.stopListening()
                            }
                        }
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isListeningState) Icons.Default.Mic else Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF131314),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Wave feedback labels
                Text(
                    text = currentVoiceStateText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFC4C7C5),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                )
            }

            // Real-time subtitles / spoken translation feedback
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color(0xFF1E1F20), shape = RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                if (userTranscription.isNotBlank()) {
                    Text(
                        text = "You: \"$userTranscription\"",
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFA8C7FA),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                Text(
                    text = if (systemOutputSpeech.isNotBlank()) systemOutputSpeech else "Syntorini is ready to converse. Say 'Hello', 'Tell me a story', 'tumi kemon acho?' or toggle questions.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (systemOutputSpeech.isNotBlank()) Color.White else Color(0xFF8E918F),
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action interactive taskbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Mute Output
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        if (isMuted) {
                            voiceManager?.stopSpeaking()
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            if (isMuted) Color(0xFFD96570).copy(alpha = 0.2f) else Color(0xFF1E1F20),
                            shape = CircleShape
                        )
                        .border(
                            1.dp,
                            if (isMuted) Color(0xFFD96570) else Color(0xFF333537),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = "Mute speaker feedback",
                        tint = if (isMuted) Color(0xFFD96570) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Push-To-Talk Microphone Trigger
                IconButton(
                    onClick = {
                        if (isListeningState) {
                            voiceManager?.stopListening()
                        } else {
                            voiceManager?.startListening()
                        }
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            if (isListeningState) Color(0xFF8AB4F8) else Color(0xFF1E1F20),
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isListeningState) Color.White else Color(0xFF333537),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isListeningState) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Record microphone inputs",
                        tint = if (isListeningState) Color(0xFF131314) else Color(0xFF8AB4F8),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // End Session Cleanly
                Button(
                    onClick = { onDismiss() },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD96570)
                    ),
                    modifier = Modifier
                        .height(54.dp)
                        .widthIn(min = 90.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
