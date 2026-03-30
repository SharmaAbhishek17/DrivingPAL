package com.drivingpal.prototype

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.telephony.SmsManager
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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
            animateClick(it)
            
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

    private fun animateClick(view: android.view.View) {
        val scaleDownX = PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 0.9f, 1f)
        val scaleDownY = PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 0.9f, 1f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleDownX, scaleDownY)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator()
        animator.start()
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
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            textView.setPadding(40, 40, 40, 40)
            textView.textSize = 16f
            
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 0, 0, 24)
            textView.layoutParams = layoutParams

            if (msg == defaultMessage) {
                highlightTextView(textView)
                selectedTextView = textView
                selectedMessage = msg
            } else {
                unhighlightTextView(textView)
            }

            textView.setOnClickListener { view ->
                animateClick(view)
                if (selectedMessage == msg) {
                    unhighlightTextView(textView)
                    selectedTextView = null
                    selectedMessage = null
                    prefs.edit().remove("defaultMessage").apply()
                } else {
                    selectedTextView?.let { prev -> unhighlightTextView(prev) }
                    highlightTextView(textView)
                    selectedTextView = textView
                    selectedMessage = msg

                    prefs.edit().putString("defaultMessage", msg).apply()
                }
            }

            savedMessagesList.addView(textView)
        }
    }

    private fun highlightTextView(textView: TextView) {
        textView.setBackgroundResource(R.drawable.bg_gradient_neon)
        textView.setTextColor(Color.BLACK)
    }

    private fun unhighlightTextView(textView: TextView) {
        textView.setBackgroundResource(R.drawable.bg_glass_card)
        textView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
