package com.safetyapp.voicetrigger

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context) {

    private val TAG = "AudioRecorder"
    private var recorder: MediaRecorder? = null
    private var outputFile: String? = null
    private var isRecording = false

    /**
     * Starts audio recording to a timestamped file in the app's external files directory.
     * Recording uses AAC codec in MPEG-4 container for wide compatibility.
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording, skipping start")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SOS_Recording_$timestamp.mp4"

        // Use app-specific external storage (no extra permissions needed on API 29+)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        outputFile = File(dir, fileName).absolutePath

        Log.d(TAG, "Recording to: $outputFile")

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile)
            try {
                prepare()
                start()
                isRecording = true
                Log.d(TAG, "Recording started ✅")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording: ${e.message}")
                release()
                recorder = null
            }
        }
    }

    /**
     * Stops the current recording and releases resources.
     * @return Path to the recorded file, or null if no recording was active.
     */
    fun stopRecording(): String? {
        if (!isRecording) return null
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            Log.d(TAG, "Recording saved: $outputFile")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            recorder?.release()
            recorder = null
            isRecording = false
            null
        }
    }

    fun isRecording() = isRecording
}
