package com.drivingpal.prototype

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.widget.Toast

class callreceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state != TelephonyManager.EXTRA_STATE_RINGING || number.isNullOrEmpty()) return

        val prefs = context.getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val lastProcessedNumber = prefs.getString("lastProcessedNumber", null)
        val lastProcessedTime = prefs.getLong("lastProcessedTime", 0L)
        val currentTime = System.currentTimeMillis()

        // Prevent rapid duplicate triggers (same number within 2 seconds)
        if (lastProcessedNumber == number && (currentTime - lastProcessedTime) < 2000) return

        val lastCaller = prefs.getString("lastCaller", null)
        val lastCallTime = prefs.getLong("lastCallTime", 0L)

        val isRepeatCall = lastCaller == number && (currentTime - lastCallTime) <= 5 * 60 * 1000

        if (isRepeatCall) {
            setDndAndSilent(context, enable = false)
            Toast.makeText(context, "🚨 Emergency call detected! DND turned OFF.", Toast.LENGTH_LONG).show()
        } else {
            setDndAndSilent(context, enable = true)
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(
                    number, null,
                    "I'm driving & my phone is on DND.Call again within 5 mins,it’ll ring!",
                    null, null
                )
                Toast.makeText(context, "📩 Auto-reply SMS sent to $number", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            prefs.edit()
                .putString("lastCaller", number)
                .putLong("lastCallTime", currentTime)
                .apply()
        }

        // Save the current processing info to avoid double processing
        prefs.edit()
            .putString("lastProcessedNumber", number)
            .putLong("lastProcessedTime", currentTime)
            .apply()
    }

    private fun setDndAndSilent(context: Context, enable: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Please grant DND access in settings.", Toast.LENGTH_LONG).show()
                return
            }

            if (enable) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                Toast.makeText(context, "🔕 DND ON — Phone Silenced", Toast.LENGTH_SHORT).show()
            } else {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Toast.makeText(context, "🔔 DND OFF — Phone Normal", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
