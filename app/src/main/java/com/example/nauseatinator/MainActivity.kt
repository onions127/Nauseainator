package com.example.nauseatinator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var isServiceRunning by remember { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            } else {
                                val serviceIntent = Intent(this@MainActivity, MotionOverlayService::class.java)
                                startForegroundService(serviceIntent)
                                isServiceRunning = true
                            }
                        } else {
                            val serviceIntent = Intent(this@MainActivity, MotionOverlayService::class.java).apply {
                                action = MotionOverlayService.ACTION_STOP_SERVICE
                            }
                            startService(serviceIntent)
                            isServiceRunning = false
                        }
                    }
                )
            }
        }
    }
}
