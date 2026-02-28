package com.safetyapp.voicetrigger

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class VoiceTriggerService : Service() {

    companion object {
        const val TAG = "VoiceTriggerService"
        const val EXTRA_TRIGGER_WORD = "trigger_word"
        const val ACTION_MANUAL_SOS = "ACTION_MANUAL_SOS"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_HEARD_TEXT = "com.safetyapp.HEARD_TEXT"
        const val EXTRA_HEARD_TEXT = "heard_text"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "voice_trigger_channel"
        private const val CHANNEL_NAME = "Safety Monitor"
        private const val RESTART_DELAY_MS = 1000L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
        private const val RECORDING_DURATION_MS = 10_000L
    }

    private var triggerWord: String = "help help help"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isSosInProgress = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var locationHelper: LocationHelper
    private lateinit var audioRecorder: AudioRecorder

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isListening && !isSosInProgress) {
                Log.d(TAG, "Watchdog: restarting listener")
                startListening()
            }
            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsHelper = PreferencesHelper(this)
        locationHelper = LocationHelper(this)
        audioRecorder = AudioRecorder(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        triggerWord = intent?.getStringExtra(EXTRA_TRIGGER_WORD) ?: prefsHelper.getTriggerWord()

        val notification = buildNotification("Monitoring for \"$triggerWord\"…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (intent?.action == ACTION_MANUAL_SOS) {
            triggerSOS("Manual SOS")
        } else if (!isListening && !isSosInProgress) {
            startListening()
            mainHandler.removeCallbacks(watchdogRunnable)
            mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
        }

        return START_STICKY
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("⚠️ Speech recognition unavailable")
            return
        }

        if (isSosInProgress) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                updateNotification("🎤 Listening for \"$triggerWord\"…")
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val text = matches.firstOrNull() ?: ""
                broadcastHeardText(text)
                if (matchesTrigger(matches)) triggerSOS(text)
                else restartListeningDelayed()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val text = matches.firstOrNull() ?: ""
                broadcastHeardText(text)
                if (matchesTrigger(matches)) triggerSOS(text)
            }

            override fun onError(error: Int) {
                isListening = false
                Log.w(TAG, "Recognizer error: $error")
                when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        mainHandler.postDelayed({ startListening() }, 2000)
                    }
                    else -> restartListeningDelayed()
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(recognizerIntent)
        isListening = true
    }

    private fun restartListeningDelayed() {
        if (isSosInProgress) return
        mainHandler.postDelayed({ if (!isListening && !isSosInProgress) startListening() }, RESTART_DELAY_MS)
    }

    private fun matchesTrigger(matches: List<String>): Boolean {
        val normalizedTrigger = triggerWord.lowercase().trim()
        val targets = listOf(normalizedTrigger, "help", "sos", "emergency", "bachao", "save me")
        return matches.any { match ->
            val m = match.lowercase()
            targets.any { m.contains(it) }
        }
    }

    private fun triggerSOS(triggerText: String) {
        if (isSosInProgress) return
        isSosInProgress = true
        isListening = false
        
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        broadcastHeardText("🚨 TRIGGERED: $triggerText")
        updateNotification("🚨 SOS ACTIVATED! Getting location...")

        serviceScope.launch {
            // Send to the special requested number
//            SmsHelper.sendSms(this@VoiceTriggerService, "+916201276870", "Hello from my app!")

            val location = locationHelper.getLastKnownLocation()
            val locationUrl = if (location != null) "https://maps.google.com/?q=${location.latitude},${location.longitude}" else "Location unavailable"
            
            val contacts = prefsHelper.getContacts()
            val message = "🚨 EMERGENCY! I need help!\nMy location: $locationUrl\nTriggered by: $triggerText"
            
            contacts.forEach { SmsHelper.sendSms(this@VoiceTriggerService, it.phone, message) }
            
            updateNotification("🚨 SOS Sent! Recording audio...")
            audioRecorder.startRecording()
            
            delay(RECORDING_DURATION_MS)
            
            audioRecorder.stopRecording()
            updateNotification("✅ SOS complete. Resuming monitor.")
            isSosInProgress = false
            startListening()
        }
    }

    private fun broadcastHeardText(text: String) {
        val intent = Intent(ACTION_HEARD_TEXT).apply {
            putExtra(EXTRA_HEARD_TEXT, text)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Monitors voice for emergency triggers"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, VoiceTriggerService::class.java).apply { action = ACTION_STOP_SERVICE }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Safety Monitor Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        audioRecorder.stopRecording()
        serviceScope.cancel()
    }
}
