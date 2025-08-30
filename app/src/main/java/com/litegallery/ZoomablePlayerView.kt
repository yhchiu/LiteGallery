package com.litegallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.ViewConfiguration
import com.google.android.exoplayer2.ui.PlayerView
import kotlin.math.*

class ZoomablePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    // Zoom limits
    private val minScale = 1f
    private val maxScale = 6f
    private var currentScale = 1f
    
    // Touch handling
    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    private var isZooming = false
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    // Transform matrix
    private val transformMatrix = Matrix()
    private val savedMatrix = Matrix()
    
    // Video bounds
    private val videoBounds = RectF()
    private val viewBounds = RectF()
    
    // Original video dimensions
    private var videoWidth = 0f
    private var videoHeight = 0f
    
    // Click listeners
    private var onVideoClickListener: (() -> Unit)? = null
    private var onVideoDoubleClickListener: (() -> Unit)? = null
    private var onZoomChangeListener: ((Float) -> Unit)? = null
    
    // Zoom levels for cycling
    private val zoomLevels = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
    private var currentZoomLevelIndex = 0

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        gestureDetector = GestureDetector(context, GestureListener())
        
        // Enable hardware acceleration for smooth scaling
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    fun setOnVideoClickListener(listener: () -> Unit) {
        onVideoClickListener = listener
    }
    
    fun setOnVideoDoubleClickListener(listener: () -> Unit) {
        onVideoDoubleClickListener = listener
    }
    
    fun setOnZoomChangeListener(listener: (Float) -> Unit) {
        onZoomChangeListener = listener
    }
    
    fun resetZoom() {
        currentScale = 1f
        currentZoomLevelIndex = 0
        transformMatrix.reset()
        applyTransformToVideoSurface()
        onZoomChangeListener?.invoke(currentScale)
        invalidate()
    }
    
    fun cycleZoom() {
        currentZoomLevelIndex = (currentZoomLevelIndex + 1) % zoomLevels.size
        val targetScale = zoomLevels[currentZoomLevelIndex]
        
        if (targetScale != currentScale) {
            val scaleFactor = targetScale / currentScale
            
            // Center the zoom
            val centerX = viewBounds.centerX()
            val centerY = viewBounds.centerY()
            
            transformMatrix.postScale(scaleFactor, scaleFactor, centerX - viewBounds.centerX(), centerY - viewBounds.centerY())
            currentScale = targetScale
            constrainTransform()
            applyTransformToVideoSurface()
            onZoomChangeListener?.invoke(currentScale)
        }
    }
    
    fun getCurrentZoomLevel(): Float {
        return currentScale
    }
    
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width.toFloat()
        videoHeight = height.toFloat()
        resetZoom()
    }
    
    private fun applyTransformToVideoSurface() {
        // Find the TextureView or SurfaceView within this PlayerView
        val videoSurface = findVideoSurface(this)
        videoSurface?.let { surface ->
            when (surface) {
                is TextureView -> {
                    surface.setTransform(transformMatrix)
                }
                // SurfaceView doesn't support matrix transforms directly
                // We handle this through the parent view's transformation
                else -> {
                    // For SurfaceView, we need to transform the container
                    surface.scaleX = currentScale
                    surface.scaleY = currentScale
                    
                    // Apply translation by adjusting the position
                    val values = FloatArray(9)
                    transformMatrix.getValues(values)
                    val translateX = values[Matrix.MTRANS_X]
                    val translateY = values[Matrix.MTRANS_Y]
                    
                    surface.translationX = translateX
                    surface.translationY = translateY
                }
            }
        }
    }
    
    private fun findVideoSurface(view: android.view.View): android.view.View? {
        if (view is TextureView || view is android.view.SurfaceView) {
            return view
        }
        
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = findVideoSurface(view.getChildAt(i))
                if (child != null) return child
            }
        }
        
        return null
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewBounds.set(0f, 0f, w.toFloat(), h.toFloat())
        resetZoom()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false
        
        // Handle scale gestures first
        handled = scaleGestureDetector.onTouchEvent(event) || handled
        
        // Handle single tap and drag gestures if not scaling
        if (!scaleGestureDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
            handled = handleDragGesture(event) || handled
        }
        
        return handled || super.onTouchEvent(event)
    }
    
    private fun handleDragGesture(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (currentScale > minScale) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        transformMatrix.postTranslate(dx, dy)
                        constrainTransform()
                        applyTransformToVideoSurface()
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        
        return false
    }
    
    private fun constrainTransform() {
        if (videoWidth <= 0 || videoHeight <= 0) return
        
        // Calculate scaled video dimensions
        val scaledWidth = videoWidth * currentScale
        val scaledHeight = videoHeight * currentScale
        
        // Get current transform values
        val values = FloatArray(9)
        transformMatrix.getValues(values)
        var translateX = values[Matrix.MTRANS_X]
        var translateY = values[Matrix.MTRANS_Y]
        
        // Calculate video position within view
        val viewCenterX = viewBounds.centerX()
        val viewCenterY = viewBounds.centerY()
        val videoCenterX = viewCenterX + translateX
        val videoCenterY = viewCenterY + translateY
        
        val videoLeft = videoCenterX - scaledWidth / 2
        val videoRight = videoCenterX + scaledWidth / 2
        val videoTop = videoCenterY - scaledHeight / 2
        val videoBottom = videoCenterY + scaledHeight / 2
        
        var deltaX = 0f
        var deltaY = 0f
        
        // Constrain horizontal movement
        if (scaledWidth <= viewBounds.width()) {
            // Video is smaller than view, center it
            deltaX = viewCenterX - videoCenterX
        } else {
            // Video is larger than view, prevent showing blank space
            if (videoLeft > viewBounds.left) {
                deltaX = viewBounds.left - videoLeft
            } else if (videoRight < viewBounds.right) {
                deltaX = viewBounds.right - videoRight
            }
        }
        
        // Constrain vertical movement
        if (scaledHeight <= viewBounds.height()) {
            // Video is smaller than view, center it
            deltaY = viewCenterY - videoCenterY
        } else {
            // Video is larger than view, prevent showing blank space
            if (videoTop > viewBounds.top) {
                deltaY = viewBounds.top - videoTop
            } else if (videoBottom < viewBounds.bottom) {
                deltaY = viewBounds.bottom - videoBottom
            }
        }
        
        if (deltaX != 0f || deltaY != 0f) {
            transformMatrix.postTranslate(deltaX, deltaY)
        }
    }
    
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZooming = true
            savedMatrix.set(transformMatrix)
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor
            
            // Constrain scale within limits
            val constrainedScaleFactor = when {
                newScale < minScale -> minScale / currentScale
                newScale > maxScale -> maxScale / currentScale
                else -> scaleFactor
            }
            
            if (constrainedScaleFactor != 1f) {
                transformMatrix.set(savedMatrix)
                
                // Calculate focus point relative to view center
                val focusX = detector.focusX - viewBounds.centerX()
                val focusY = detector.focusY - viewBounds.centerY()
                
                transformMatrix.postScale(constrainedScaleFactor, constrainedScaleFactor, focusX, focusY)
                
                currentScale = newScale.coerceIn(minScale, maxScale)
                constrainTransform()
                applyTransformToVideoSurface()
                onZoomChangeListener?.invoke(currentScale)
            }
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isZooming = false
            savedMatrix.set(transformMatrix)
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isZooming && !isDragging) {
                onVideoClickListener?.invoke()
                return true
            }
            return false
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isZooming && !isDragging) {
                // Only trigger video double-click for play/pause, no zoom
                onVideoDoubleClickListener?.invoke()
                return true
            }
            return false
        }
    }
}