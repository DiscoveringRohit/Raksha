package com.safetyapp.voicetrigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Auto-restarts the VoiceTriggerService after device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted — checking if service should restart")
            val prefs = PreferencesHelper(context)
            val contacts = prefs.getContacts()

            if (contacts.isNotEmpty()) {
                Log.d("BootReceiver", "Auto-starting Voice Trigger Service after boot")
                val serviceIntent = Intent(context, VoiceTriggerService::class.java).apply {
                    putExtra(VoiceTriggerService.EXTRA_TRIGGER_WORD, prefs.getTriggerWord())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
