package com.drivingpal.prototype

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.*
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.content.Context
import androidx.core.content.ContextCompat

class AccidentDialogActivity : AppCompatActivity() {

    private lateinit var vibrator: Vibrator
    private var isCancelled = false
    private val emergencyNumber = "1234567890" // Replace with real number

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showAccidentDialog()

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(30000)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isCancelled) {
                vibrator.cancel()
                makeEmergencyCall()
                sendEmergencySMS()
                finish()
            }
        }, 30000)
    }

    private fun showAccidentDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accident Detected")
            .setMessage("Are you okay?\n\nIf you don’t cancel in 30 seconds, emergency contacts will be notified.")
            .setCancelable(false)
            .setPositiveButton("I’m Okay") { _, _ ->
                isCancelled = true
                vibrator.cancel()
                Toast.makeText(this, "Emergency alert cancelled", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    private fun makeEmergencyCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$emergencyNumber")
            startActivity(callIntent)
        } else {
            Toast.makeText(this, "CALL_PHONE permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmergencySMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val message = "⚠️ Possible accident detected. Please check immediately."
            SmsManager.getDefault().sendTextMessage(emergencyNumber, null, message, null, null)
        } else {
            Toast.makeText(this, "SEND_SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
