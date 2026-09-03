package com.example.nauseatinator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MotionDotsView(context: Context) : View(context), Choreographer.FrameCallback {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // Semi-transparent small black dots
        style = Paint.Style.FILL
    }
    
    private val dotRadius = 12f
    private val dotMargin = 40f
    private val numDots = 12

    private var targetOffsetX = 0f
    private var targetOffsetY = 0f
    
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f

    private var coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var isRendering = false
    
    // Simple spring/damping physics
    private val stiffness = 0.15f
    private val damping = 0.75f
    private var velocityX = 0f
    private var velocityY = 0f

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRendering()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRendering()
    }

    fun startRendering() {
        if (!isRendering) {
            isRendering = true
            coroutineScope = CoroutineScope(Dispatchers.Main + Job())
            coroutineScope.launch {
                MotionOverlayService.motionDataFlow.collectLatest { data ->
                    // Simulate physical inertia: dots shift visually opposite to the device's acceleration
                    val scaleFactor = 25f
                    targetOffsetX = -data.accelX * scaleFactor
                    targetOffsetY = -data.accelY * scaleFactor
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopRendering() {
        if (isRendering) {
            isRendering = false
            coroutineScope.coroutineContext[Job]?.cancel()
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (isRendering) {
            updatePhysics()
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updatePhysics() {
        // Calculate the force pulling the dot towards its target offset
        val forceX = (targetOffsetX - currentOffsetX) * stiffness
        val forceY = (targetOffsetY - currentOffsetY) * stiffness

        // Apply force to velocity and dampen it
        velocityX = (velocityX + forceX) * damping
        velocityY = (velocityY + forceY) * damping

        // Move the current position
        currentOffsetX += velocityX
        currentOffsetY += velocityY
        
        // Ensure that when the phone stops moving, the dots gently return to baseline (0,0) offset
        // by making the target slowly decay to 0 if no new data pushes it away.
        // Actually, since targetOffsetX is driven directly by the low-pass filtered accel (which returns to 0 on resting),
        // we don't need a manual decay here.
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val spacing = height / (numDots + 1f)
        
        for (i in 1..numDots) {
            val baseY = i * spacing
            
            // Left margin dot
            canvas.drawCircle(
                dotMargin + currentOffsetX, 
                baseY + currentOffsetY, 
                dotRadius, 
                paint
            )
            
            // Right margin dot
            canvas.drawCircle(
                width - dotMargin + currentOffsetX, 
                baseY + currentOffsetY, 
                dotRadius, 
                paint
            )
        }
    }
}
