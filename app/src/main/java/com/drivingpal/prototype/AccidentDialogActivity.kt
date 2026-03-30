package com.drivingpal.prototype

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import android.app.NotificationManager
import android.media.AudioManager

class AccidentDialogActivity : AppCompatActivity() {

    private lateinit var vibrator: Vibrator
    private var isCancelled = false
    private var emergencyContacts = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accident)

        val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val contacts = prefs.getStringSet("emergencyContacts", emptySet()) ?: emptySet()
        for (contact in contacts) {
            val parts = contact.split(": ")
            emergencyContacts.add(if (parts.size > 1) parts.last() else contact)
        }
        if (emergencyContacts.isEmpty()) {
            emergencyContacts.add("911")
        }

        setupAccidentUI()
        
        // Immediately forcefully dismantle any DND layers so vibration explicitly sounds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(30000, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect, audioAttributes)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30000, audioAttributes)
        }

        val handler = Handler(Looper.getMainLooper())
        var timeLeft = 30
        val countdownText = findViewById<TextView>(R.id.countdownText)

        handler.post(object : Runnable {
            override fun run() {
                if (isCancelled) return
                if (timeLeft > 0) {
                    countdownText.text = timeLeft.toString()
                    timeLeft--
                    handler.postDelayed(this, 1000)
                } else {
                    vibrator.cancel()
                    // Time out: ONLY Send SMS. Do not auto-call.
                    sendEmergencySMSWithLocation()
                }
            }
        })
    }

    private fun setupAccidentUI() {
        val cancelBtn = findViewById<Button>(R.id.cancelEmergencyBtn)
        val callBtn = findViewById<Button>(R.id.callEmergencyBtn)

        cancelBtn.setOnClickListener {
            isCancelled = true
            vibrator.cancel()
            Toast.makeText(this, "Emergency alert cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }

        callBtn.setOnClickListener {
            isCancelled = true
            vibrator.cancel()
            // Manual click: Send SMS and also explicitly make call
            sendEmergencySMSWithLocation()
            makeEmergencyCall()
        }
    }

    private fun makeEmergencyCall() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:${emergencyContacts.first()}")
            startActivity(callIntent)
        }
        // No toast, call is optional
    }

    private fun sendEmergencySMSWithLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        val smsSender = { locString: String ->
            val message = "Emergency detected! Possible accident. My location: $locString"
            try {
                for (number in emergencyContacts) {
                    SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            finish()
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            try {
                fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        smsSender("https://maps.google.com/?q=${location.latitude},${location.longitude}")
                    } else {
                        // Fallback
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                            if (lastLoc != null) {
                                smsSender("https://maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}")
                            } else {
                                smsSender("Location unavailable")
                            }
                        }.addOnFailureListener {
                            smsSender("Location unavailable")
                        }
                    }
                }.addOnFailureListener {
                    smsSender("Location unavailable")
                }
            } catch (e: Exception) {
                smsSender("Location unavailable")
            }
        } else {
            smsSender("Location unavailable (Permissions)")
        }
    }
}
