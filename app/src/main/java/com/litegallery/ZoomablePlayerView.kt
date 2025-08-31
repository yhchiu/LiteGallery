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

    // Fitted content size within the view at scale=1 (resize_mode="fit")
    private var baseContentWidth = 0f
    private var baseContentHeight = 0f
    private var fitScale = 1f
    
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
        // Ensure redraw even when video is paused
        invalidate()
    }
    
    fun cycleZoom() {
        android.util.Log.d("ZoomablePlayerView", "cycleZoom called - current index: $currentZoomLevelIndex, scale: $currentScale")
        currentZoomLevelIndex = (currentZoomLevelIndex + 1) % zoomLevels.size
        val targetScale = zoomLevels[currentZoomLevelIndex]
        
        android.util.Log.d("ZoomablePlayerView", "Target scale: $targetScale, currentScale: $currentScale")
        
        if (targetScale != currentScale) {
            val scaleFactor = targetScale / currentScale
            
            // Center the zoom (convert view center to TextureView-local coordinates)
            val centerX = viewBounds.centerX()
            val centerY = viewBounds.centerY()
            val tvLeft = (viewBounds.width() - baseContentWidth) / 2f
            val tvTop = (viewBounds.height() - baseContentHeight) / 2f
            val localPivotX = if (baseContentWidth > 0f) centerX - tvLeft else centerX
            val localPivotY = if (baseContentHeight > 0f) centerY - tvTop else centerY
            transformMatrix.postScale(scaleFactor, scaleFactor, localPivotX, localPivotY)
            currentScale = targetScale
            constrainTransform()
            applyTransformToVideoSurface()
            onZoomChangeListener?.invoke(currentScale)
            android.util.Log.d("ZoomablePlayerView", "Zoom cycled to ${currentScale}x")
            // Ensure redraw when paused
            invalidate()
        } else {
            android.util.Log.d("ZoomablePlayerView", "Scale unchanged")
        }
    }
    
    fun getCurrentZoomLevel(): Float {
        return currentScale
    }
    
    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width.toFloat()
        videoHeight = height.toFloat()
        recomputeBaseContentSize()
        resetZoom()
    }
    
    private fun applyTransformToVideoSurface() {
        android.util.Log.d("ZoomablePlayerView", "applyTransformToVideoSurface called, scale: $currentScale")
        // Find the TextureView or SurfaceView within this PlayerView
        val videoSurface = findVideoSurface(this)
        android.util.Log.d("ZoomablePlayerView", "Video surface found: ${videoSurface != null}, type: ${videoSurface?.javaClass?.simpleName}")
        
        videoSurface?.let { surface ->
            when (surface) {
                is TextureView -> {
                    android.util.Log.d("ZoomablePlayerView", "Applying transform to TextureView")
                    surface.setTransform(transformMatrix)
                    // Force redraw so transform applies even when paused
                    surface.invalidate()
                }
                // SurfaceView doesn't support matrix transforms directly
                // We handle this through the parent view's transformation
                else -> {
                    android.util.Log.d("ZoomablePlayerView", "Applying scale to SurfaceView")
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
                    surface.invalidate()
                }
            }
        }
        // Also invalidate this container view to ensure UI redraw
        invalidate()
    }

    private fun recomputeBaseContentSize() {
        if (videoWidth <= 0f || videoHeight <= 0f || viewBounds.width() <= 0f || viewBounds.height() <= 0f) {
            baseContentWidth = 0f
            baseContentHeight = 0f
            fitScale = 1f
            return
        }
        // Fit scale reflects how PlayerView fits the video into the view when scale=1
        val scaleX = viewBounds.width() / videoWidth
        val scaleY = viewBounds.height() / videoHeight
        fitScale = min(scaleX, scaleY)
        baseContentWidth = videoWidth * fitScale
        baseContentHeight = videoHeight * fitScale
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
        recomputeBaseContentSize()
        resetZoom()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Prevent parent (e.g., ViewPager2) from intercepting when zooming/dragging
        if (currentScale > minScale || scaleGestureDetector.isInProgress || isDragging) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }

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

        // Ensure base content size is known
        if (baseContentWidth == 0f || baseContentHeight == 0f) {
            recomputeBaseContentSize()
        }

        // TextureView is laid out as baseContentWidth/Height centered inside the view
        val tvLeft = (viewBounds.width() - baseContentWidth) / 2f
        val tvTop = (viewBounds.height() - baseContentHeight) / 2f

        // Work in TextureView local coordinates for mapping
        val baseLocalRect = RectF(0f, 0f, baseContentWidth, baseContentHeight)
        val mappedLocalRect = RectF()
        transformMatrix.mapRect(mappedLocalRect, baseLocalRect)
        // Convert to parent (PlayerView) coordinates
        val mappedRect = RectF(
            mappedLocalRect.left + tvLeft,
            mappedLocalRect.top + tvTop,
            mappedLocalRect.right + tvLeft,
            mappedLocalRect.bottom + tvTop
        )

        var deltaX = 0f
        var deltaY = 0f

        val viewW = viewBounds.width()
        val viewH = viewBounds.height()

        // Horizontal constraint
        if (mappedRect.width() <= viewW) {
            // Center horizontally
            val targetLeft = (viewW - mappedRect.width()) / 2f
            deltaX = targetLeft - mappedRect.left
        } else {
            if (mappedRect.left > 0f) {
                deltaX = -mappedRect.left
            } else if (mappedRect.right < viewW) {
                deltaX = viewW - mappedRect.right
            }
        }

        // Vertical constraint
        if (mappedRect.height() <= viewH) {
            // Center vertically
            val targetTop = (viewH - mappedRect.height()) / 2f
            deltaY = targetTop - mappedRect.top
        } else {
            if (mappedRect.top > 0f) {
                deltaY = -mappedRect.top
            } else if (mappedRect.bottom < viewH) {
                deltaY = viewH - mappedRect.bottom
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
                
                // Convert gesture focus from view to TextureView-local coordinates
                val tvLeft = (viewBounds.width() - baseContentWidth) / 2f
                val tvTop = (viewBounds.height() - baseContentHeight) / 2f
                val focusX = detector.focusX - tvLeft
                val focusY = detector.focusY - tvTop
                transformMatrix.postScale(constrainedScaleFactor, constrainedScaleFactor, focusX, focusY)
                
                currentScale = newScale.coerceIn(minScale, maxScale)
                constrainTransform()
                applyTransformToVideoSurface()
                onZoomChangeListener?.invoke(currentScale)
                // Ensure redraw when paused
                invalidate()
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
