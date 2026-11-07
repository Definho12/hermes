package com.guilherme.hermes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** Text shared from another app ("Compartilhar" → Hermes), waiting to go into the box. */
    private var incoming by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Engine.init(this)
        if (savedInstanceState == null) take(intent)
        askNotifications()
        setContent {
            HermesTheme {
                HermesApp(incoming) { incoming = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT) ?: i.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!t.isNullOrBlank()) incoming = t
        }
    }

    /** The progress notification needs permission on Android 13+ (asked once). */
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("hermes", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotif", false)) return
        prefs.edit().putBoolean("askedNotif", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
