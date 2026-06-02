package com.example.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onSpeechListeningStateChanged: (Boolean) -> Unit,
    private val onTtsSpeakingStateChanged: (Boolean) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) {
    private val TAG = "VoiceManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Speech Recognizer: Ready")
                        onSpeechListeningStateChanged(true)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech Recognizer: Beginning")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Can be used for custom sound wave styling if needed
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech Recognizer: End of speech")
                        onSpeechListeningStateChanged(false)
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client-side compiler error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions denied for recording"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue occurred during decoding"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech timing out"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Live engine is busy"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Could not process vocal inputs"
                        }
                        Log.e(TAG, "Speech Recognizer Error: $message ($error)")
                        onSpeechListeningStateChanged(false)
                        // Only report relevant error feedback to avoid interrupting live session flow
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onErrorOccurred(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0]
                            Log.d(TAG, "Speech Input Result: $spokenText")
                            onSpeechRecognized(spokenText)
                        } else {
                            Log.d(TAG, "Speech matches are empty")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            Log.e(TAG, "Native Speech Recognition is not supported on this device.")
        }
    }

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                tts?.apply {
                    // Try to configure localized languages (fallback to device preferences)
                    val result = setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "English is missing or not supported on this TTS engine.")
                    }
                    
                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            onTtsSpeakingStateChanged(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            onTtsSpeakingStateChanged(false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            onTtsSpeakingStateChanged(false)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            onTtsSpeakingStateChanged(false)
                            Log.e(TAG, "TTS Utterance Error: $errorCode")
                        }
                    })
                }
            } else {
                Log.e(TAG, "Failed to initialize standard Android Text to Speech engine.")
            }
        }
    }

    /**
     * Triggers active voice listener.
     */
    fun startListening() {
        stopSpeaking() // Ensure Syntorini stops talking while user speaks
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting voice capture", e)
            onErrorOccurred("Could not initialize microphone scanner.")
        }
    }

    /**
     * Halts active listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        onSpeechListeningStateChanged(false)
    }

    /**
     * Synthesizes audio speech using custom model output
     */
    fun speak(text: String) {
        if (!isTtsInitialized) {
            Log.e(TAG, "TTS service is not ready.")
            return
        }

        try {
            // Clean up basic markdown formatting tags from responses for clean pronunciation
            val cleanText = text
                .replace(Regex("\\*\\*"), "") // remove bold markers
                .replace(Regex("###"), "") // remove subheaders
                .replace(Regex("\\*"), "") // remove bullets
                .replace(Regex("`"), "") // remove code ticks
                .trim()

            // Best effort language detection for Banglish/Bengali
            val hasBengaliChars = cleanText.any { it.code in 0x0980..0x09FF }
            val customLocale = if (hasBengaliChars) {
                Locale("bn", "BD")
            } else {
                Locale.US
            }
            tts?.language = customLocale

            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "SyntoriniLiveVoiceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in speech output path", e)
        }
    }

    /**
     * Halts speech playback
     */
    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
            onTtsSpeakingStateChanged(false)
        }
    }

    /**
     * Safe cleanups
     */
    fun destroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}
