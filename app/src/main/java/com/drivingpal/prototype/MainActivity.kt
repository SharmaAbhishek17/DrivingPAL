package com.drivingpal.prototype
import com.drivingpal.prototype.AccidentDetectionService
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 101
    private var isServiceRunning = false
    private val callReceiver = callreceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissionsAndDndAccess()

        val startBtn = findViewById<Button>(R.id.startServiceBtn)
        val stopBtn = findViewById<Button>(R.id.stopServiceBtn)

        startBtn.setOnClickListener {
            if (!isServiceRunning) {
                // Register call receiver
                val filter = IntentFilter().apply {
                    addAction("android.intent.action.PHONE_STATE")
                    addAction("android.intent.action.NEW_OUTGOING_CALL")
                }
                registerReceiver(callReceiver, filter)

                // ✅ Start Accident Detection Service
                val serviceIntent = Intent(this, AccidentDetectionService::class.java)
                startService(serviceIntent)

                // ✅ TEMPORARY: Directly launch AccidentDialogActivity for testing
                val testIntent = Intent(this, AccidentDialogActivity::class.java)
                startActivity(testIntent)

                isServiceRunning = true
                Toast.makeText(this, "Services Started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show()
            }
        }

        stopBtn.setOnClickListener {
            if (isServiceRunning) {
                unregisterReceiver(callReceiver)

                // ✅ Stop Accident Detection Service
                val stopIntent = Intent(this, AccidentDetectionService::class.java)
                stopService(stopIntent)

                isServiceRunning = false
                Toast.makeText(this, "Services Stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndDndAccess() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.CALL_PHONE, // ✅ Added
            Manifest.permission.ACCESS_FINE_LOCATION // ✅ Added
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
                Toast.makeText(
                    this,
                    "Please grant Do Not Disturb permission for DrivingPal.",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }
    }
}
