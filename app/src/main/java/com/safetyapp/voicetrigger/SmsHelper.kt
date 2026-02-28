package com.safetyapp.voicetrigger

import android.content.Context
import android.telephony.SmsManager
import android.os.Build
import android.widget.Toast
import android.util.Log

object SmsHelper {
    private const val TAG = "SmsHelper"

    fun sendSms(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // If message is too long, split it into parts
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                Log.d(TAG, "Multipart SMS sent to $phoneNumber (${parts.size} parts)")
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Log.d(TAG, "SMS sent to $phoneNumber")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber: ${e.message}")
            e.printStackTrace()
        }
    }
}
