package com.drivingpal.prototype

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drivingpal.prototype.data.AppDatabase
import kotlinx.coroutines.launch
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.OvershootInterpolator
import android.media.AudioManager

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private var isServiceRunning = false
    private val callReceiver = callreceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissionsAndDndAccess()

        val startBtn = findViewById<View>(R.id.startServiceBtn)
        val stopBtn = findViewById<View>(R.id.stopServiceBtn)

        startBtn.setOnClickListener { view ->
            animateClick(view)
            if (!isServiceRunning) {
                val filter = IntentFilter().apply {
                    addAction("android.intent.action.PHONE_STATE")
                    addAction("android.intent.action.NEW_OUTGOING_CALL")
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(callReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(callReceiver, filter)
                }

                val serviceIntent = Intent(this, AccidentDetectionService::class.java)
                startService(serviceIntent)
                
                isServiceRunning = true
                
                val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("isDriveModeActive", true).apply()
                updateStatusText()
                
                Toast.makeText(this, "Drive Mode Started", Toast.LENGTH_SHORT).show()
                
                startActivity(Intent(this, DriveModeActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DriveModeActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        stopBtn.setOnClickListener { view ->
            animateClick(view)
            if (isServiceRunning) {
                try { unregisterReceiver(callReceiver) } catch (e: Exception) {}
                stopService(Intent(this, AccidentDetectionService::class.java))
                isServiceRunning = false
                
                val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("isDriveModeActive", false).apply()
                updateStatusText()
                
                restorePhoneState()

                Toast.makeText(this, "Drive Mode Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.addPriorityBtn).setOnClickListener { view ->
            animateClick(view)
            val intent = Intent(this, EmergencyContactsActivity::class.java)
            intent.putExtra("EXTRA_CONTACT_TYPE", "priority")
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<Button>(R.id.addEmergencyBtn).setOnClickListener { view ->
            animateClick(view)
            val intent = Intent(this, EmergencyContactsActivity::class.java)
            intent.putExtra("EXTRA_CONTACT_TYPE", "emergency")
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val viewLogsBtn = findViewById<Button>(R.id.viewEmergencyBtn)
        viewLogsBtn.text = "View Call Logs"
        viewLogsBtn.setOnClickListener { view ->
            animateClick(view)
            startActivity(Intent(this, CallLogsActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<Button>(R.id.customizeSmsBtn).setOnClickListener { view ->
            animateClick(view)
            startActivity(Intent(this, SmsCustomizationActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        findViewById<Button>(R.id.testAccidentBtn).setOnClickListener { view ->
            animateClick(view)
            startActivity(Intent(this, AccidentDialogActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        
        findViewById<Button>(R.id.enableDriveModeBtn).visibility = View.GONE
        findViewById<Button>(R.id.testEmergencyAlertBtn).visibility = View.GONE
        findViewById<Button>(R.id.sendLocationBtn).visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        
        val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val isDriveModeActive = prefs.getBoolean("isDriveModeActive", false)
        if (!isDriveModeActive && isServiceRunning) {
            // User hit the exit drive mode button while service is active, clean it up
            try { unregisterReceiver(callReceiver) } catch (e: Exception) {}
            stopService(Intent(this, AccidentDetectionService::class.java))
            isServiceRunning = false
            restorePhoneState()
        }
        
        updateDashboardStats()
        updateStatusText()
    }

    private fun animateClick(view: View) {
        val scaleDownX = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1f)
        val scaleDownY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleDownX, scaleDownY)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator()
        animator.start()
    }

    private fun restorePhoneState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun updateStatusText() {
        val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val isDriveModeActive = prefs.getBoolean("isDriveModeActive", false)
        val statusText = findViewById<TextView>(R.id.statusText)
        if (isDriveModeActive) {
            statusText.text = "Status: Active (Driving Mode)"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        } else {
            statusText.text = "Status: Not Driving"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun updateDashboardStats() {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val blocked = db.callLogDao().getCountByStatus("BLOCKED")
            val autoReplies = blocked 
            
            findViewById<TextView>(R.id.callsBlockedText).text = blocked.toString()
            findViewById<TextView>(R.id.autoRepliesText).text = autoReplies.toString()
        }
    }

    private fun checkPermissionsAndDndAccess() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToAsk = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToAsk.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToAsk.toTypedArray(), PERMISSION_CODE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }
    }
}
