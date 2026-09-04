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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BF000000") // 75% opacity black
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF") // 40% opacity white outline
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val numDotsPerColumn = 12
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

        val bucketHeight = height.toFloat() / numDotsPerColumn

        for (i in 0 until numDotsPerColumn) {
            val bucketStartY = i * bucketHeight
            val bucketEndY = (i + 1) * bucketHeight

            // --- LEFT SIDE ---
            // Outer column
            var y = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            var x = Random.nextFloat() * 20f + 10f // Between 10 and 30 px from edge
            var radius = Random.nextFloat() * 6f + 8f
            dots.add(Dot(x, y, radius))

            // Inner column
            y = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            x = Random.nextFloat() * 20f + 50f // Between 50 and 70 px from edge
            radius = Random.nextFloat() * 6f + 8f
            dots.add(Dot(x, y, radius))

            // --- RIGHT SIDE ---
            // Outer column
            y = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            x = width - (Random.nextFloat() * 20f + 10f)
            radius = Random.nextFloat() * 6f + 8f
            dots.add(Dot(x, y, radius))

            // Inner column
            y = Random.nextFloat() * (bucketEndY - bucketStartY) + bucketStartY
            x = width - (Random.nextFloat() * 20f + 50f)
            radius = Random.nextFloat() * 6f + 8f
            dots.add(Dot(x, y, radius))
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
        val forceX = (targetOffsetX - currentOffsetX) * stiffness
        val forceY = (targetOffsetY - currentOffsetY) * stiffness

        velocityX = (velocityX + forceX) * damping
        velocityY = (velocityY + forceY) * damping

        currentOffsetX += velocityX
        currentOffsetY += velocityY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (dot in dots) {
            // Draw the black fill
            canvas.drawCircle(
                dot.x + currentOffsetX, 
                dot.y + currentOffsetY, 
                dot.radius, 
                fillPaint
            )
            // Draw the white outline (halo)
            canvas.drawCircle(
                dot.x + currentOffsetX,
                dot.y + currentOffsetY,
                dot.radius,
                strokePaint
            )
        }
    }
}
