package com.safetyapp.voicetrigger

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.safetyapp.voicetrigger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private latei  nit var binding: ActivityMainBinding
    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var contactsAdapter: ContactsAdapter

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }.toTypedArray()
    }

    private val heardTextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(VoiceTriggerService.EXTRA_HEARD_TEXT) ?: return
            binding.tvHeardText.text = text
            binding.tvLastHeard.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsHelper = PreferencesHelper(this)
        setupUI()
        setupRecyclerView()
        loadSavedData()
        
        binding.switchService.isChecked = isServiceRunning(VoiceTriggerService::class.java)
        updateStatusText(binding.switchService.isChecked)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(VoiceTriggerService.ACTION_HEARD_TEXT)
        // Specify RECEIVER_NOT_EXPORTED for internal app broadcasts (Required for Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(heardTextReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(heardTextReceiver, filter)
        }
        
        val running = isServiceRunning(VoiceTriggerService::class.java)
        if (binding.switchService.isChecked != running) {
            binding.switchService.isChecked = running
            updateStatusText(running)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(heardTextReceiver)
    }

    private fun setupUI() {
        binding.btnSaveTrigger.setOnClickListener {
            val word = binding.etTriggerWord.text.toString().trim()
            if (word.isEmpty()) { showToast("Please enter a trigger word"); return@setOnClickListener }
            prefsHelper.saveTriggerWord(word)
            showToast("✅ Trigger word saved: \"$word\"")
        }

        binding.btnAddContact.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            val phone = binding.etContactPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                showToast("Please fill in both name and phone number"); return@setOnClickListener
            }
            val contacts = prefsHelper.getContacts().toMutableList()
            contacts.add(EmergencyContact(name, phone))
            prefsHelper.saveContacts(contacts)
            contactsAdapter.updateContacts(contacts)
            updateContactsVisibility(contacts)
            binding.etContactName.text?.clear()
            binding.etContactPhone.text?.clear()
            showToast("✅ Contact added: $name")
        }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasAllPermissions()) {
                    startVoiceTriggerService()
                } else {
                    requestPermissionsWithRationale()
                    binding.switchService.isChecked = false
                }
            } else {
                stopVoiceTriggerService()
            }
            updateStatusText(binding.switchService.isChecked)
        }

        binding.btnSOS.setOnClickListener { confirmAndSendSOS() }
    }

    private fun updateStatusText(isRunning: Boolean) {
        if (isRunning) {
            binding.tvStatus.text = "⬤  Shield Active — Listening…"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
            binding.tvLastHeard.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = "⬤  Shield Inactive"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvLastHeard.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter(mutableListOf()) { contact ->
            val contacts = prefsHelper.getContacts().toMutableList()
            contacts.remove(contact)
            prefsHelper.saveContacts(contacts)
            contactsAdapter.updateContacts(contacts)
            updateContactsVisibility(contacts)
        }
        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactsAdapter
        }
    }

    private fun loadSavedData() {
        binding.etTriggerWord.setText(prefsHelper.getTriggerWord())
        val contacts = prefsHelper.getContacts()
        contactsAdapter.updateContacts(contacts)
        updateContactsVisibility(contacts)
    }

    private fun updateContactsVisibility(contacts: List<EmergencyContact>) {
        binding.tvNoContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
        binding.rvContacts.visibility = if (contacts.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun startVoiceTriggerService() {
        val contacts = prefsHelper.getContacts()
        if (contacts.isEmpty()) {
            showToast("⚠️ Add at least one emergency contact first!")
            binding.switchService.isChecked = false
            return
        }
        val intent = Intent(this, VoiceTriggerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopVoiceTriggerService() {
        stopService(Intent(this, VoiceTriggerService::class.java))
    }

    private fun confirmAndSendSOS() {
        if (prefsHelper.getContacts().isEmpty()) {
            showToast("⚠️ Add emergency contacts first!")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("🚨 Send SOS Alert?")
            .setMessage("This will send your location to all emergency contacts.")
            .setPositiveButton("SEND") { _, _ ->
                val intent = Intent(this, VoiceTriggerService::class.java).apply {
                    action = VoiceTriggerService.ACTION_MANUAL_SOS
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasAllPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsWithRationale() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("Background Location Access")
                .setMessage("This app needs background location access to track you even when the screen is off. Please select 'Allow all the time' in the next screen.")
                .setPositiveButton("Grant") { _, _ ->
                    ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
