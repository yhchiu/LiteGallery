package org.iurl.litegallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowInsets
import androidx.appcompat.widget.AppCompatImageView
import androidx.preference.PreferenceManager
import kotlin.math.*

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // Transformation matrix applied to the ImageView
    private var transformMatrix = Matrix()
    
    // Zoom limits (relative to fit-center base scale)
    private val minScale = 1f
    private var currentScale = 1f
    private var baseScale = 1f

    // Get max scale from preferences
    private val maxScale: Float
        get() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString("zoom_max_scale", "3")?.toFloatOrNull() ?: 3f
        }
    
    // Touch handling
    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector
    private var isZooming = false
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    // Image bounds
    private val imageRect = RectF()
    private val viewRect = RectF()
    
    // Click listener for single tap
    private var onImageClickListener: (() -> Unit)? = null
    private var onZoomChangeListener: ((Float) -> Unit)? = null
    
    // Zoom levels for cycling (dynamically generated based on maxScale)
    private val zoomLevels: FloatArray
        get() {
            val max = maxScale.toInt()
            return (1..max).map { it.toFloat() }.toFloatArray()
        }
    private var currentZoomLevelIndex = 0

    init {
        scaleType = ScaleType.MATRIX
        
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        gestureDetector = GestureDetector(context, GestureListener())
        
        // Enable hardware acceleration for smooth scaling
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }
    
    fun setOnImageClickListener(listener: () -> Unit) {
        onImageClickListener = listener
    }
    
    fun setOnZoomChangeListener(listener: (Float) -> Unit) {
        onZoomChangeListener = listener
    }
    
    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }
    
    fun resetZoom() {
        currentScale = 1f
        currentZoomLevelIndex = 0
        transformMatrix.reset()
        fitImageToView()
        onZoomChangeListener?.invoke(1f)
    }
    
    fun cycleZoom() {
        currentZoomLevelIndex = (currentZoomLevelIndex + 1) % zoomLevels.size
        val targetScale = zoomLevels[currentZoomLevelIndex]
        
        if (targetScale != currentScale) {
            val viewportRect = getViewportRect()
            val viewWidth = viewportRect.width()
            val viewHeight = viewportRect.height()
            
            if (viewWidth <= 0 || viewHeight <= 0) return
            val scaleFactor = targetScale / currentScale
            
            // Center the zoom
            val centerX = viewportRect.centerX()
            val centerY = viewportRect.centerY()
            
            transformMatrix.postScale(scaleFactor, scaleFactor, centerX, centerY)
            currentScale = targetScale
            constrainImageBounds()
            // Apply to the actual ImageView
            this.imageMatrix = transformMatrix
            onZoomChangeListener?.invoke(currentScale)
        }
    }
    
    fun getCurrentZoomLevel(): Float {
        return currentScale
    }
    
    private fun fitImageToView() {
        val drawable = drawable ?: return
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
        
        // Calculate scale to fit image in view (fitCenter behavior)
        val scaleX = viewWidth / imageWidth
        val scaleY = viewHeight / imageHeight
        baseScale = min(scaleX, scaleY)
        
        // Center the image
        val dx = (viewWidth - imageWidth * baseScale) * 0.5f
        val dy = (viewHeight - imageHeight * baseScale) * 0.5f
        
        transformMatrix.setScale(baseScale, baseScale)
        transformMatrix.postTranslate(dx, dy)

        currentScale = 1f
        // Apply to the actual ImageView
        this.imageMatrix = transformMatrix
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
                        constrainImageBounds()
                        // Apply to the actual ImageView
                        this.imageMatrix = transformMatrix
                        
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
    
    private fun constrainImageBounds() {
        val drawable = drawable ?: return
        
        // Get current image bounds
        imageRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        transformMatrix.mapRect(imageRect)
        
        // Get view bounds
        val viewportRect = getViewportRect()
        viewRect.set(viewportRect)
        
        var deltaX = 0f
        var deltaY = 0f
        
        // Constrain horizontal movement
        if (imageRect.width() <= viewRect.width()) {
            // Image is smaller than view, center it
            deltaX = viewRect.centerX() - imageRect.centerX()
        } else {
            // Image is larger than view, prevent showing blank space
            if (imageRect.left > viewRect.left) {
                deltaX = viewRect.left - imageRect.left
            } else if (imageRect.right < viewRect.right) {
                deltaX = viewRect.right - imageRect.right
            }
        }
        
        // Constrain vertical movement
        if (imageRect.height() <= viewRect.height()) {
            // Image is smaller than view, center it
            deltaY = viewRect.centerY() - imageRect.centerY()
        } else {
            // Image is larger than view, prevent showing blank space
            if (imageRect.top > viewRect.top) {
                deltaY = viewRect.top - imageRect.top
            } else if (imageRect.bottom < viewRect.bottom) {
                deltaY = viewRect.bottom - imageRect.bottom
            }
        }
        
        if (deltaX != 0f || deltaY != 0f) {
            transformMatrix.postTranslate(deltaX, deltaY)
        }
    }
    private fun getViewportRect(): RectF {
        val fullWidth = width.toFloat()
        val fullHeight = height.toFloat()
        if (fullWidth <= 0f || fullHeight <= 0f) {
            return RectF(0f, 0f, fullWidth, fullHeight)
        }

        var insetLeft = 0f
        var insetRight = 0f
        var insetBottom = 0f

        val insets = rootWindowInsets
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val navInsets = insets.getInsets(WindowInsets.Type.navigationBars())
                insetLeft = navInsets.left.toFloat()
                insetRight = navInsets.right.toFloat()
                insetBottom = navInsets.bottom.toFloat()
            } else {
                @Suppress("DEPRECATION")
                run {
                    insetLeft = insets.systemWindowInsetLeft.toFloat()
                    insetRight = insets.systemWindowInsetRight.toFloat()
                    insetBottom = insets.systemWindowInsetBottom.toFloat()
                }
            }
        }

        val left = insetLeft.coerceAtLeast(0f)
        val top = 0f
        val right = (fullWidth - insetRight).coerceAtLeast(left + 1f)
        val bottom = (fullHeight - insetBottom).coerceAtLeast(top + 1f)
        return RectF(left, top, right, bottom)
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZooming = true
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val constrainedScaleFactor = newScale / currentScale
            
            // Constrain scale within limits
            if (constrainedScaleFactor != 1f) {
                transformMatrix.postScale(
                    constrainedScaleFactor, 
                    constrainedScaleFactor, 
                    detector.focusX, 
                    detector.focusY
                )

                currentScale = newScale
                constrainImageBounds()
                // Apply to the actual ImageView
                this@ZoomImageView.imageMatrix = transformMatrix
                onZoomChangeListener?.invoke(currentScale)
            }
            
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isZooming = false
        }
    }
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isZooming && !isDragging) {
                onImageClickListener?.invoke()
                return true
            }
            return false
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale > minScale) {
                // Zoom out to fit
                resetZoom()
            } else {
                // Zoom in to 2x
                val targetScale = min(maxScale, minScale * 2f)
                val scaleFactor = targetScale / currentScale
                
                transformMatrix.postScale(scaleFactor, scaleFactor, e.x, e.y)
                currentScale = targetScale
                constrainImageBounds()
                // Apply to the actual ImageView
                this@ZoomImageView.imageMatrix = transformMatrix
                onZoomChangeListener?.invoke(currentScale)
            }
            return true
        }
    }
}

