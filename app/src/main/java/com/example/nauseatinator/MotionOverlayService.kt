package com.example.nauseatinator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MotionData(val accelX: Float = 0f, val accelY: Float = 0f, val gyroZ: Float = 0f)

class MotionOverlayService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private var dotsView: MotionDotsView? = null

    private lateinit var sensorManager: SensorManager
    private var linearAccSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var isSensorRegistered = false

    // Low-pass filter state and constant
    private var smoothedAccelX = 0f
    private var smoothedAccelY = 0f
    private var smoothedGyroZ = 0f
    private val alpha = 0.1f

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseService()
                Intent.ACTION_SCREEN_ON -> resumeService()
            }
        }
    }

    companion object {
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        private const val CHANNEL_ID = "MotionOverlayChannel"
        private const val NOTIFICATION_ID = 1

        private val _motionDataFlow = MutableStateFlow(MotionData())
        val motionDataFlow: StateFlow<MotionData> = _motionDataFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        linearAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        resumeService()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showOverlay()
    }

    private fun pauseService() {
        if (isSensorRegistered) {
            sensorManager.unregisterListener(this)
            isSensorRegistered = false
        }
        dotsView?.stopRendering()
    }

    private fun resumeService() {
        if (!isSensorRegistered) {
            linearAccSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            isSensorRegistered = true
        }
        dotsView?.startRendering()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                smoothedAccelX = alpha * event.values[0] + (1 - alpha) * smoothedAccelX
                smoothedAccelY = alpha * event.values[1] + (1 - alpha) * smoothedAccelY
            }
            Sensor.TYPE_GYROSCOPE -> {
                smoothedGyroZ = alpha * event.values[2] + (1 - alpha) * smoothedGyroZ
            }
        }
        
        _motionDataFlow.value = MotionData(smoothedAccelX, smoothedAccelY, smoothedGyroZ)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Motion Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains the motion sickness visual overlay"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, MotionOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motion Overlay Active")
            .setContentText("Tap Stop to remove the overlay.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showOverlay() {
        if (dotsView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        dotsView = MotionDotsView(this)
        windowManager.addView(dotsView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseService()
        unregisterReceiver(screenStateReceiver)
        dotsView?.let {
            windowManager.removeView(it)
        }
        dotsView = null
    }
}
