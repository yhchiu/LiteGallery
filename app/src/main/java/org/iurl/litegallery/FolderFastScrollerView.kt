package org.iurl.litegallery

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.android.material.R as MaterialR
import kotlin.math.max
import kotlin.math.min

class FolderFastScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface DragListener {
        fun onDragStarted(): Boolean = true
        fun onDragMoved(fraction: Float)
        fun onDragStopped()
    }

    private enum class State {
        Hidden,
        VisibleIdle,
        Dragging,
        FadingOut
    }

    private val density = resources.displayMetrics.density
    private val thumbWidth = 4f.dp
    private val draggingThumbWidth = 6f.dp
    private val minThumbHeight = 48f.dp
    private val thumbRightInset = 8f.dp
    private val trackVerticalInset = 4f.dp
    private val touchZoneWidth = 48f.dp
    private val trackWidth = 2f.dp
    private val bubbleGap = 12f.dp
    private val bubbleVerticalGap = 12f.dp
    private val bubbleHorizontalPadding = 12f.dp
    private val bubbleHeight = 34f.dp
    private val bubbleCornerRadius = 17f.dp
    private val bubbleEdgeInset = 8f.dp
    private val maxBubbleWidth = 120f.dp
    private val fadeDelayMs = 1_000L
    private val fadeDurationMs = 180L

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRect = RectF()
    private val trackRect = RectF()
    private val bubbleRect = RectF()

    private var state = State.Hidden
    private var enabledForContent = true
    private var scrollOffset = 0
    private var scrollRange = 0
    private var scrollExtent = 0
    private var thumbAlpha = 0f
    private var sections: List<FastScrollSection> = emptyList()
    private var currentSectionTitle: String? = null
    private var cachedDisplayTitle = ""
    private var dragListener: DragListener? = null
    private var fadeAnimator: ValueAnimator? = null
    private var dragThumbCenterOffset = 0f
    private var lockedDragScrollRange = 0
    private var lockedDragScrollExtent = 0
    private var dragThumbFraction: Float? = null

    private val fadeRunnable = Runnable { startFadeOut() }

    private val Float.dp: Float
        get() = this * density

    init {
        isClickable = false
        isFocusable = false
        contentDescription = context.getString(R.string.fast_scroll_thumb_content_description)

        val accentColor = org.iurl.litegallery.theme.ThemeColorResolver.resolveColor(
            context,
            MaterialR.attr.colorPrimary
        )
        val bubbleTextColor = org.iurl.litegallery.theme.ThemeColorResolver.resolveColor(
            context,
            MaterialR.attr.colorOnPrimary
        )

        thumbPaint.color = accentColor
        trackPaint.color = accentColor
        bubblePaint.color = accentColor
        bubbleTextPaint.color = bubbleTextColor
        bubbleTextPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            15f,
            resources.displayMetrics
        )
    }

    fun setDragListener(listener: DragListener?) {
        dragListener = listener
    }

    fun setScrollMetrics(offset: Int, range: Int, extent: Int) {
        scrollOffset = offset.coerceAtLeast(0)
        scrollRange = range.coerceAtLeast(0)
        scrollExtent = extent.coerceAtLeast(0)

        if (!isContentScrollable() && state != State.Hidden) {
            forceHidden(notifyDragStopped = true)
            return
        }
        postInvalidateOnAnimation()
    }

    fun setSections(sections: List<FastScrollSection>) {
        this.sections = sections
        if (sections.isEmpty()) {
            setCurrentSectionTitle(null)
        }
    }

    fun setCurrentSectionTitle(title: String?) {
        val sanitizedTitle = title?.takeIf { sections.isNotEmpty() && it.isNotBlank() }
        if (currentSectionTitle == sanitizedTitle) return

        currentSectionTitle = sanitizedTitle
        updateCachedDisplayTitle()
        postInvalidateOnAnimation()
    }

    fun show() {
        if (!enabledForContent || !isContentScrollable()) return

        removeCallbacks(fadeRunnable)
        fadeAnimator?.cancel()
        state = if (state == State.Dragging) State.Dragging else State.VisibleIdle
        thumbAlpha = 1f
        postInvalidateOnAnimation()
    }

    fun hideDelayed() {
        if (!enabledForContent || state == State.Dragging || !isContentScrollable()) return

        removeCallbacks(fadeRunnable)
        postDelayed(fadeRunnable, fadeDelayMs)
    }

    fun setEnabledForContent(enabled: Boolean) {
        if (enabledForContent == enabled) {
            if (!enabled && state != State.Hidden) {
                forceHidden(notifyDragStopped = true)
            }
            return
        }

        enabledForContent = enabled
        if (!enabled) {
            forceHidden(notifyDragStopped = true)
        } else {
            postInvalidateOnAnimation()
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateCachedDisplayTitle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (thumbAlpha <= 0f || !enabledForContent || !isContentScrollable()) return
        if (!updateThumbRect()) return

        val alpha = (thumbAlpha * 255).toInt().coerceIn(0, 255)
        if (state == State.Dragging) {
            trackPaint.alpha = (alpha * 0.32f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(trackRect, trackWidth, trackWidth, trackPaint)
        }

        thumbPaint.alpha = alpha
        val radius = thumbRect.width() / 2f
        canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint)

        if (state == State.Dragging && cachedDisplayTitle.isNotEmpty()) {
            drawBubble(canvas, alpha)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForContent) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_MOVE -> {
                if (state != State.Dragging) return false
                dispatchDragMove(event.y)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state != State.Dragging) return false
                stopDragging()
                true
            }
            else -> state == State.Dragging
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fadeRunnable)
        fadeAnimator?.cancel()
        fadeAnimator = null
        forceHidden(notifyDragStopped = true)
        super.onDetachedFromWindow()
    }

    private fun handleActionDown(event: MotionEvent): Boolean {
        if (!isContentScrollable() || thumbAlpha <= 0f || !isInTouchZone(event.x)) return false
        if (!updateThumbRect() || !isInThumbTouchTarget(event.y)) return false
        if (dragListener?.onDragStarted() == false) return false

        parent?.requestDisallowInterceptTouchEvent(true)
        removeCallbacks(fadeRunnable)
        fadeAnimator?.cancel()
        state = State.Dragging
        thumbAlpha = 1f
        dragThumbCenterOffset = event.y - thumbRect.centerY()
        lockedDragScrollRange = scrollRange
        lockedDragScrollExtent = scrollExtent
        dragThumbFraction = thumbScrollFraction()
        postInvalidateOnAnimation()
        return true
    }

    private fun stopDragging() {
        parent?.requestDisallowInterceptTouchEvent(false)
        dragListener?.onDragStopped()
        clearDragMetricLock()
        state = State.VisibleIdle
        postInvalidateOnAnimation()
        hideDelayed()
    }

    private fun dispatchDragMove(y: Float) {
        if (!updateThumbRect()) return

        val thumbHalfHeight = thumbRect.height() / 2f
        val top = paddingTop + thumbHalfHeight
        val bottom = height - paddingBottom - thumbHalfHeight
        val available = (bottom - top).coerceAtLeast(1f)
        val thumbCenterY = y - dragThumbCenterOffset
        val fraction = ((thumbCenterY - top) / available).coerceIn(0f, 1f)
        dragThumbFraction = fraction
        dragListener?.onDragMoved(fraction)
        postInvalidateOnAnimation()
    }

    private fun drawBubble(canvas: Canvas, alpha: Int) {
        val textWidth = bubbleTextPaint.measureText(cachedDisplayTitle)
        val bubbleWidth = (textWidth + (bubbleHorizontalPadding * 2f))
            .coerceAtMost(maxAllowedBubbleWidth())
        var bubbleRight = thumbRect.left - bubbleGap
        var bubbleLeft = bubbleRight - bubbleWidth
        val minLeft = paddingLeft + bubbleEdgeInset
        if (bubbleLeft < minLeft) {
            bubbleLeft = minLeft
            bubbleRight = bubbleLeft + bubbleWidth
        }

        val minTop = paddingTop + bubbleEdgeInset
        val maxTop = height - paddingBottom - bubbleEdgeInset - bubbleHeight
        val preferredTop = thumbRect.top - bubbleVerticalGap - bubbleHeight
        val bubbleTop = if (maxTop >= minTop) {
            preferredTop.coerceIn(minTop, maxTop)
        } else {
            minTop
        }
        bubbleRect.set(
            bubbleLeft,
            bubbleTop,
            bubbleRight,
            bubbleTop + bubbleHeight
        )

        bubblePaint.alpha = alpha
        canvas.drawRoundRect(bubbleRect, bubbleCornerRadius, bubbleCornerRadius, bubblePaint)

        bubbleTextPaint.alpha = alpha
        val baseline = bubbleRect.centerY() -
            ((bubbleTextPaint.descent() + bubbleTextPaint.ascent()) / 2f)
        canvas.drawText(
            cachedDisplayTitle,
            bubbleRect.left + bubbleHorizontalPadding,
            baseline,
            bubbleTextPaint
        )
    }

    private fun updateThumbRect(): Boolean {
        val effectiveRange = if (state == State.Dragging && lockedDragScrollRange > 0) {
            lockedDragScrollRange
        } else {
            scrollRange
        }
        val effectiveExtent = if (state == State.Dragging && lockedDragScrollExtent > 0) {
            lockedDragScrollExtent
        } else {
            scrollExtent
        }
        val scrollableRange = effectiveRange - effectiveExtent
        if (effectiveExtent <= 0 || effectiveRange <= 0 || scrollableRange <= 0) return false

        val trackTop = paddingTop + trackVerticalInset
        val trackBottom = height - paddingBottom - trackVerticalInset
        val trackHeight = trackBottom - trackTop
        if (trackHeight <= 0f) return false

        val thumbHeight = max(minThumbHeight, trackHeight * (effectiveExtent.toFloat() / effectiveRange))
            .coerceAtMost(trackHeight)
        val progress = if (state == State.Dragging) {
            dragThumbFraction ?: thumbScrollFraction(scrollableRange)
        } else {
            thumbScrollFraction(scrollableRange)
        }
        val thumbTop = trackTop + ((trackHeight - thumbHeight) * progress)
        val visualThumbWidth = if (state == State.Dragging) draggingThumbWidth else thumbWidth
        val thumbRight = width - paddingRight - thumbRightInset
        val thumbLeft = thumbRight - visualThumbWidth

        thumbRect.set(thumbLeft, thumbTop, thumbRight, thumbTop + thumbHeight)
        val trackLeft = thumbRight - trackWidth
        trackRect.set(trackLeft, trackTop, thumbRight, trackBottom)
        return true
    }

    private fun updateCachedDisplayTitle() {
        val title = currentSectionTitle
        if (title.isNullOrEmpty()) {
            cachedDisplayTitle = ""
            return
        }

        val textWidth = (maxAllowedBubbleWidth() - (bubbleHorizontalPadding * 2f))
            .coerceAtLeast(1f)
        cachedDisplayTitle = TextUtils.ellipsize(
            title,
            bubbleTextPaint,
            textWidth,
            TextUtils.TruncateAt.END
        ).toString()
    }

    private fun maxAllowedBubbleWidth(): Float {
        val widthLimit = if (width > 0) width * 0.33f else maxBubbleWidth
        return min(maxBubbleWidth, widthLimit).coerceAtLeast(48f.dp)
    }

    private fun isContentScrollable(): Boolean {
        return enabledForContent && scrollExtent > 0 && scrollRange > scrollExtent
    }

    private fun isInTouchZone(x: Float): Boolean {
        return x >= width - paddingRight - touchZoneWidth
    }

    private fun thumbScrollFraction(scrollableRange: Int = scrollRange - scrollExtent): Float {
        if (scrollableRange <= 0) return 0f
        return scrollOffset.coerceIn(0, scrollableRange).toFloat() / scrollableRange
    }

    private fun isInThumbTouchTarget(y: Float): Boolean {
        val extraHeight = ((touchZoneWidth - thumbRect.height()) / 2f).coerceAtLeast(0f)
        return y >= thumbRect.top - extraHeight && y <= thumbRect.bottom + extraHeight
    }

    private fun startFadeOut() {
        if (!enabledForContent || state == State.Dragging || !isContentScrollable()) return

        fadeAnimator?.cancel()
        state = State.FadingOut
        fadeAnimator = ValueAnimator.ofFloat(thumbAlpha, 0f).apply {
            duration = fadeDurationMs
            addUpdateListener { animator ->
                thumbAlpha = animator.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (state == State.FadingOut) {
                        state = State.Hidden
                        thumbAlpha = 0f
                        postInvalidateOnAnimation()
                    }
                }
            })
            start()
        }
    }

    private fun forceHidden(notifyDragStopped: Boolean) {
        removeCallbacks(fadeRunnable)
        fadeAnimator?.cancel()
        fadeAnimator = null
        if (notifyDragStopped && state == State.Dragging) {
            parent?.requestDisallowInterceptTouchEvent(false)
            dragListener?.onDragStopped()
        }
        clearDragMetricLock()
        state = State.Hidden
        thumbAlpha = 0f
        postInvalidateOnAnimation()
    }

    private fun clearDragMetricLock() {
        lockedDragScrollRange = 0
        lockedDragScrollExtent = 0
        dragThumbCenterOffset = 0f
        dragThumbFraction = null
    }
}
