package com.drivingpal.prototype

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SmsCustomizationActivity : AppCompatActivity() {

    private lateinit var messageEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var savedMessagesList: LinearLayout
    private var selectedTextView: TextView? = null
    private var selectedMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_customization)

        messageEditText = findViewById(R.id.messageEditText)
        saveButton = findViewById(R.id.saveMessageBtn)
        savedMessagesList = findViewById(R.id.savedMessagesList)

        saveButton.setOnClickListener {
            val message = messageEditText.text.toString()
            if (message.isNotEmpty()) {
                saveMessageToPreferences(message)
                messageEditText.text.clear()
                loadSavedMessages()
                Toast.makeText(this, "Message saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        loadSavedMessages()
    }

    private fun saveMessageToPreferences(message: String) {
        val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("savedMessages", mutableSetOf()) ?: mutableSetOf()
        existing.add(message)
        prefs.edit().putStringSet("savedMessages", existing).apply()
    }

    private fun loadSavedMessages() {
        savedMessagesList.removeAllViews()
        val prefs = getSharedPreferences("DrivingPalPrefs", Context.MODE_PRIVATE)
        val messages = prefs.getStringSet("savedMessages", setOf()) ?: setOf()
        val defaultMessage = prefs.getString("defaultMessage", "")

        messages.forEach { msg ->
            val textView = TextView(this)
            textView.text = msg
            textView.setPadding(10, 20, 10, 20)
            textView.textSize = 16f
            textView.setBackgroundResource(R.drawable.message_selector_bg)

            // If previously selected
            if (msg == defaultMessage) {
                highlightTextView(textView)
                selectedTextView = textView
                selectedMessage = msg
            }

            textView.setOnClickListener {
                if (selectedMessage == msg) {
                    // Unselect if clicked again
                    unhighlightTextView(textView)
                    selectedTextView = null
                    selectedMessage = null
                    prefs.edit().remove("defaultMessage").apply()
                } else {
                    // Highlight new
                    selectedTextView?.let { prev -> unhighlightTextView(prev) }
                    highlightTextView(textView)
                    selectedTextView = textView
                    selectedMessage = msg

                    prefs.edit().putString("defaultMessage", msg).apply()

                    val lastNumber = prefs.getString("lastCallerNumber", null)
                    if (lastNumber != null) {
                        try {
                            SmsManager.getDefault().sendTextMessage(lastNumber, null, msg, null, null)
                            Toast.makeText(this, "Message sent to $lastNumber", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Failed to send SMS", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No recent caller number found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            savedMessagesList.addView(textView)
        }
    }

    // Subtle highlight using hardcoded transparent yellow
    private fun highlightTextView(textView: TextView) {
        textView.setBackgroundColor(Color.parseColor("#40FFFF00")) // 25% transparent yellow
    }

    private fun unhighlightTextView(textView: TextView) {
        textView.setBackgroundResource(R.drawable.message_selector_bg)
    }
}
