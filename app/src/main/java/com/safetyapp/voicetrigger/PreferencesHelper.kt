package com.safetyapp.voicetrigger

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class EmergencyContact(
    val name: String,
    val phone: String
)

class PreferencesHelper(context: Context) {

    companion object {
        private const val PREFS_NAME = "SafetyAppPrefs"
        private const val KEY_TRIGGER_WORD = "trigger_word"
        private const val KEY_CONTACTS = "emergency_contacts"
        private const val DEFAULT_TRIGGER_WORD = "help help help"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Trigger Word ─────────────────────────────────────────────────────────

    fun saveTriggerWord(word: String) {
        prefs.edit().putString(KEY_TRIGGER_WORD, word.lowercase().trim()).apply()
    }

    fun getTriggerWord(): String {
        return prefs.getString(KEY_TRIGGER_WORD, DEFAULT_TRIGGER_WORD) ?: DEFAULT_TRIGGER_WORD
    }

    // ─── Emergency Contacts ───────────────────────────────────────────────────

    fun saveContacts(contacts: List<EmergencyContact>) {
        val json = Gson().toJson(contacts)
        prefs.edit().putString(KEY_CONTACTS, json).apply()
    }

    fun getContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_CONTACTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<EmergencyContact>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
