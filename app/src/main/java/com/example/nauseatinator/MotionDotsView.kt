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
import kotlin.random.Random

class MotionDotsView(context: Context) : View(context), Choreographer.FrameCallback {

    private data class Dot(var x: Float, var y: Float, var radius: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BF000000") // 75% opacity black
        style = Paint.Style.FILL
    }
    
    private val numDots = 12
    private val dots = mutableListOf<Dot>()

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generateDots(w, h)
    }

    private fun generateDots(width: Int, height: Int) {
        dots.clear()
        if (width == 0 || height == 0) return

        val bucketHeight = height.toFloat() / numDots

        for (i in 0 until numDots) {
            val bucketStartY = i * bucketHeight
            val bucketEndY = (i + 1) * bucketHeight

            // Left side dot
            val leftY = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            val leftX = Random.nextFloat() * 40f + 10f // Random X between 10 and 50
            val leftRadius = Random.nextFloat() * 6f + 8f // Random radius between 8 and 14
            dots.add(Dot(leftX, leftY, leftRadius))

            // Right side dot
            val rightY = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            val rightX = width - (Random.nextFloat() * 40f + 10f) // Random X between width-50 and width-10
            val rightRadius = Random.nextFloat() * 6f + 8f // Random radius between 8 and 14
            dots.add(Dot(rightX, rightY, rightRadius))
        }
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
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (dot in dots) {
            canvas.drawCircle(
                dot.x + currentOffsetX, 
                dot.y + currentOffsetY, 
                dot.radius, 
                paint
            )
        }
    }
}
