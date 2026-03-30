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
import com.drivingpal.prototype.data.AppDatabase
import com.drivingpal.prototype.data.CallLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object RingtonePlayer {
    var ringtone: android.media.Ringtone? = null

    fun play(context: Context) {
        try {
            if (ringtone == null) {
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            }
            if (ringtone?.isPlaying == false) {
                ringtone?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class callreceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        
        if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            RingtonePlayer.stop()
        }

        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state != TelephonyManager.EXTRA_STATE_RINGING || number.isNullOrEmpty()) return

        val prefs = context.getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("lastCallerNumber", number).apply()
        
        val lastProcessedNumber = prefs.getString("lastProcessedNumber", null)
        val lastProcessedTime = prefs.getLong("lastProcessedTime", 0L)
        val currentTime = System.currentTimeMillis()

        if (lastProcessedNumber == number && (currentTime - lastProcessedTime) < 2000) return

        val lastCaller = prefs.getString("lastCaller", null)
        val lastCallTime = prefs.getLong("lastCallTime", 0L)
        val isRepeatCall = lastCaller == number && (currentTime - lastCallTime) <= 5 * 60 * 1000

        val priorityContacts = prefs.getStringSet("priorityContacts", emptySet()) ?: emptySet()
        val isPriority = priorityContacts.any { it.contains(number) }

        val db = AppDatabase.getDatabase(context)

        if (isPriority || isRepeatCall) {
            setDndAndSound(context, enableDnd = false, maxVolume = true)
            RingtonePlayer.play(context)
            Toast.makeText(context, "🚨 Emergency/Priority call detected! DND OFF. Ringing...", Toast.LENGTH_LONG).show()
            
            CoroutineScope(Dispatchers.IO).launch {
                db.callLogDao().insertCallLog(CallLogEntity(phoneNumber = number, timestamp = currentTime, status = "EMERGENCY"))
            }
        } else {
            setDndAndSound(context, enableDnd = true, maxVolume = false)
            
            val customMsg = prefs.getString("defaultMessage", "I am currently driving and my phone is on DND. Please call again within 5 minutes if it's urgent.")
            
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(number, null, customMsg, null, null)
                Toast.makeText(context, "📩 Auto-reply SMS sent", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { }

            prefs.edit()
                .putString("lastCaller", number)
                .putLong("lastCallTime", currentTime)
                .apply()
                
            CoroutineScope(Dispatchers.IO).launch {
                db.callLogDao().insertCallLog(CallLogEntity(phoneNumber = number, timestamp = currentTime, status = "BLOCKED"))
            }
        }

        prefs.edit()
            .putString("lastProcessedNumber", number)
            .putLong("lastProcessedTime", currentTime)
            .apply()
    }

    private fun setDndAndSound(context: Context, enableDnd: Boolean, maxVolume: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) return

            if (enableDnd) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            } else {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                
                if (maxVolume) {
                    val maxVolumeLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolumeLevel, 0)
                }
            }
        }
    }
}
