package com.litao.slider

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.annotation.*
import androidx.annotation.IntRange
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.res.getColorStateListOrThrow
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import androidx.core.math.MathUtils
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.litao.slider.anim.ThumbValueAnimation
import com.litao.slider.anim.TipViewAnimator
import com.litao.slider.thumb.DefaultThumbDrawable
import com.litao.slider.widget.TipViewContainer
import java.lang.reflect.InvocationTargetException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * @author : litao
 * @date   : 2023/2/13 16:21
 */
abstract class BaseSlider constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    View(context, attrs, defStyleAttr) {

    private var mOrientation = HORIZONTAL

    private var trackPaint: Paint
    private var trackSecondaryPaint: Paint
    private var ticksPaint: Paint
    private var inactiveTicksPaint: Paint
    private var inactiveTrackPaint: Paint
    private var haloPaint: Paint
    private var debugPaint: Paint


    private lateinit var trackColor: ColorStateList
    private  var trackStartColor: ColorStateList?=null
    private  var trackEndColor: ColorStateList?=null
    private  var trackCenterColor: ColorStateList?=null

    private lateinit var trackSecondaryColor: ColorStateList
    private lateinit var trackColorInactive: ColorStateList
    private lateinit var ticksColor: ColorStateList
    private lateinit var ticksColorInactive: ColorStateList
    private lateinit var thumbTextColor: ColorStateList
    private lateinit var haloColor: ColorStateList

    private val defaultThumbDrawable = DefaultThumbDrawable()
    private var customThumbDrawable: Drawable? = null

    private var thumbWidth = UNSET
    private var thumbHeight = UNSET
    private var thumbVOffset = 0
    private var thumbElevation = 0f
    private var isThumbWithinTrackBounds = false
    private val thumbAnimation = ThumbValueAnimation()

    private var enableDrawHalo = true
    private var haloDrawable: RippleDrawable? = null
    private var haloRadius = 0
    private var tickRadius = 0f


    private val trackRectF = RectF()
    private val inactiveTrackRectF = RectF()
    private val viewRectF = RectF()
    private var thumbOffset = 0

    private var trackInnerHPadding = 0
    private var enableAutoHPadding = true
    private var trackInnerVPadding = 0
    private var trackCornerRadius = UNSET


    private var lastTouchEvent: MotionEvent? = null
    private var scaledTouchSlop = 0
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownDiff = 0f
    private var isDragging = false
    private var isTackingStart = false

    private var tipView: TipViewContainer = TipViewContainer(context)
    private var isShowTipView = false

    private var hasDirtyData = false

    var enableHapticFeedback = false

    /**
     * Ignore the global setting for whether to perform haptic feedback.Only effective below API 33
     */
    var ignoreGlobalHapticFeedbackSetting = false
    var enableProgressAnim = false

    /**
     * Whether the progress value changes continuously as the user slides.
     */
    var isConsecutiveProgress = false
    var valueFrom = 0f
        set(value) {
            if (field != value) {
                field = value
                hasDirtyData = true
                postInvalidate()
            }
        }

    var valueTo = 0f
        set(value) {
            if (field != value) {
                field = value
                hasDirtyData = true
                postInvalidate()
            }
        }

    var value = 0f
        private set

    var secondaryValue = 0f
        private set

    var stepSize = 0.0f
        set(value) {
            if (field != value && value > 0) {
                field = value
                hasDirtyData = true
                postInvalidate()
            }
        }

    var tickVisible = false

    //用户设置的宽高
    private var sourceViewHeight = 0
    private var sourceViewWidth = 0

    //修正后的真实高度，会根据thumb、thumb shadow、track的高度来进行调整
    private var viewHeight = 0
    private var viewWidth = 0

    var trackThickness = 0
        set(@IntRange(from = 0) value) {
            if (value != field) {
                field = value
                updateViewLayout()
            }
        }

    var trackHeight = 0
    var trackWidth = 0

    private var progressAnimator = ValueAnimator()

    private var sliderTouchMode = MODE_NORMAL

    companion object {
        var DEBUG_MODE = false

        const val UNSET = -1

        private const val HIGH_QUALITY_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG

        private const val HALO_ALPHA = 63

        //Slider touch mode
        private const val MODE_NORMAL = 0
        private const val MODE_DISABLE_TOUCH = 1
        private const val MODE_DISABLE_CLICK_TOUCH = 2

        const val HORIZONTAL: Int = 0
        const val VERTICAL: Int = 1



    }

    abstract fun updateDirtyData()

    abstract fun onStartTacking()
    abstract fun onStopTacking()

    abstract fun onDrawBefore(canvas: Canvas, trackRect: RectF, trackCenter: Float)
    abstract fun onDrawAfter(canvas: Canvas, trackRect: RectF, trackCenter: Float)

    abstract fun onValueChanged(value: Float, fromUser: Boolean)

    abstract fun dispatchDrawInactiveTrackBefore(canvas: Canvas, trackRect: RectF, trackCenter: Float): Boolean
    abstract fun drawInactiveTrackAfter(canvas: Canvas, trackRect: RectF, trackCenter: Float)

    abstract fun dispatchDrawTrackBefore(
        canvas: Canvas,
        trackRect: RectF,
        inactiveTrackRect: RectF,
        trackCenter: Float
    ): Boolean

    abstract fun drawTrackAfter(canvas: Canvas, trackRect: RectF, inactiveTrackRect: RectF, trackCenter: Float)

    abstract fun dispatchDrawSecondaryTrackBefore(canvas: Canvas, trackRect: RectF, inactiveTrackRect: RectF, trackCenter: Float): Boolean
    abstract fun drawSecondaryTrackAfter(canvas: Canvas, trackRect: RectF, inactiveTrackRect: RectF, trackCenter: Float)

    abstract fun dispatchDrawIndicatorsBefore(canvas: Canvas, trackRect: RectF, trackCenter: Float): Boolean
    abstract fun dispatchDrawIndicatorBefore(canvas: Canvas, trackRect: RectF, indicatorPoint: PointF, index:Int): Boolean
    abstract fun drawIndicatorAfter(canvas: Canvas, trackRect: RectF, indicatorPoint: PointF, index:Int)
    abstract fun drawIndicatorsAfter(canvas: Canvas, trackRect: RectF, trackCenter: Float)



    abstract fun dispatchDrawThumbBefore(canvas: Canvas, cx: Float, cy: Float): Boolean
    abstract fun drawThumbAfter(canvas: Canvas, cx: Float, cy: Float)


    init {
        inactiveTrackPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        trackPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        trackSecondaryPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        ticksPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        inactiveTicksPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        haloPaint = Paint(HIGH_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }

        debugPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

        processAttributes(context, attrs, defStyleAttr)

        thumbAnimation.apply {
            addUpdateListener {
                val value = getAnimatedValueAbsolute()
                adjustThumbDrawableBounds(
                    (value * thumbWidth).toInt(),
                    (value * thumbHeight).toInt()
                )
                postInvalidate()
            }
        }

        progressAnimator.apply {
            this.duration = 300L
            addUpdateListener {
                val progress = it.animatedValue.toString().toFloat()
                this@BaseSlider.value = progress
                this.interpolator = LinearOutSlowInInterpolator()
                valueChanged(progress, isDragging)
                updateHaloHotspot()
                postInvalidate()
                hasDirtyData = true
            }
            doOnEnd {
                onProgressAnimEnd()
            }
        }
    }

    private fun processAttributes(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int) {
        context.withStyledAttributes(attrs, R.styleable.NiftySlider, defStyleAttr, R.style.Widget_NiftySlider) {
            mOrientation = getInt(R.styleable.NiftySlider_android_orientation, HORIZONTAL)
            valueFrom = getFloat(R.styleable.NiftySlider_android_valueFrom, 0.0f)
            valueTo = getFloat(R.styleable.NiftySlider_android_valueTo, 1.0f)
            value = getFloat(R.styleable.NiftySlider_android_value, 0.0f)
            stepSize = getFloat(R.styleable.NiftySlider_android_stepSize, 0.0f)

            tickVisible = getBoolean(R.styleable.NiftySlider_ticksVisible, false)
            enableHapticFeedback = getBoolean(R.styleable.NiftySlider_android_hapticFeedbackEnabled, false)
            ignoreGlobalHapticFeedbackSetting = getBoolean(R.styleable.NiftySlider_ignoreGlobalHapticFeedbackSetting,false)

            sourceViewHeight = getLayoutDimension(R.styleable.NiftySlider_android_layout_height, 0)
            sourceViewWidth = getLayoutDimension(R.styleable.NiftySlider_android_layout_width, 0)
            trackThickness = getDimensionPixelOffset(R.styleable.NiftySlider_trackThickness, 0)

            //Compat older versions
            if (trackThickness <= 0){
                trackThickness = getDimensionPixelOffset(R.styleable.NiftySlider_trackHeight, 0)
            }

            enableProgressAnim = getBoolean(R.styleable.NiftySlider_enableProgressAnim, false)
            isConsecutiveProgress = getBoolean(R.styleable.NiftySlider_isConsecutiveProgress, false)

            setTrackTintList(
                getColorStateList(R.styleable.NiftySlider_trackColor) ?: AppCompatResources.getColorStateList(
                    context,
                    R.color.default_track_color
                )
            )
            setTrackSecondaryTintList(
                getColorStateList(R.styleable.NiftySlider_trackSecondaryColor) ?: AppCompatResources.getColorStateList(
                    context,
                    R.color.default_track_color
                )
            )
            trackStartColor = getColorStateList(R.styleable.NiftySlider_trackStartColor)
            trackEndColor = getColorStateList(R.styleable.NiftySlider_trackEndColor)
            trackCenterColor = getColorStateList(R.styleable.NiftySlider_trackCenterColor)

            setTrackInactiveTintList(
                getColorStateList(R.styleable.NiftySlider_trackColorInactive) ?: AppCompatResources.getColorStateList(
                    context,
                    R.color.default_track_inactive_color
                )
            )

            setTicksTintList(
                getColorStateList(R.styleable.NiftySlider_ticksColor) ?: AppCompatResources.getColorStateList(
                    context,
                    R.color.default_ticks_color
                )
            )
            setTicksInactiveTintList(
                getColorStateList(R.styleable.NiftySlider_ticksColorInactive) ?: AppCompatResources.getColorStateList(
                    context,
                    R.color.default_ticks_inactive_color
                )
            )


            val thumbW = getDimensionPixelOffset(R.styleable.NiftySlider_thumbWidth, UNSET)
            val thumbH = getDimensionPixelOffset(R.styleable.NiftySlider_thumbHeight, UNSET)

            setThumbTintList(getColorStateListOrThrow(R.styleable.NiftySlider_thumbColor))
            thumbRadius = getDimensionPixelOffset(R.styleable.NiftySlider_thumbRadius, 0)
            setThumbWidthAndHeight(thumbW, thumbH)

            setThumbVOffset(getDimensionPixelOffset(R.styleable.NiftySlider_thumbVOffset, 0))
            setThumbWithinTrackBounds(getBoolean(R.styleable.NiftySlider_thumbWithinTrackBounds, false))
            setThumbElevation(getDimension(R.styleable.NiftySlider_thumbElevation, 0f))
            setThumbShadowColor(getColor(R.styleable.NiftySlider_thumbShadowColor, Color.GRAY))
            setThumbStrokeColor(getColorStateList(R.styleable.NiftySlider_thumbStrokeColor))
            setThumbStrokeWidth(getDimension(R.styleable.NiftySlider_thumbStrokeWidth, 0f))
            setThumbText(getString(R.styleable.NiftySlider_thumbText) ?: "")
            setThumbIcon(getDrawable(R.styleable.NiftySlider_thumbIcon))
            setThumbIconSize(getDimensionPixelOffset(R.styleable.NiftySlider_thumbIconSize, UNSET))
            setThumbIconTintColor(getColor(R.styleable.NiftySlider_thumbIconTintColor, UNSET))
            setThumbTextTintList(
                getColorStateList(R.styleable.NiftySlider_thumbTextColor) ?: ColorStateList.valueOf(
                    Color.WHITE
                )
            )
            setThumbTextSize(getDimension(R.styleable.NiftySlider_thumbTextSize, 10f))
            setThumbTextBold(getBoolean(R.styleable.NiftySlider_thumbTextBold, false))

            setEnableAutoHPadding(getBoolean(R.styleable.NiftySlider_enableAutoHPadding, true))
            setTrackInnerHPadding(getDimensionPixelOffset(R.styleable.NiftySlider_trackInnerHPadding, UNSET))
            setTrackInnerVPadding(getDimensionPixelOffset(R.styleable.NiftySlider_trackInnerVPadding, UNSET))
            setTrackCornersRadius(getDimensionPixelOffset(R.styleable.NiftySlider_trackCornersRadius, UNSET))
            setEnableDrawHalo(getBoolean(R.styleable.NiftySlider_enableDrawHalo, true))
            setHaloTintList(getColorStateListOrThrow(R.styleable.NiftySlider_haloColor))
            setHaloRadius(getDimensionPixelOffset(R.styleable.NiftySlider_haloRadius, 0))
            setTickRadius(getDimension(R.styleable.NiftySlider_tickRadius, 0.0f))

            setTipViewVisibility(getBoolean(R.styleable.NiftySlider_tipViewVisible, false))
            setTipVerticalOffset(getDimensionPixelOffset(R.styleable.NiftySlider_tipViewVerticalOffset, 0))
            setTipBackground(getColor(R.styleable.NiftySlider_tipViewBackground, Color.WHITE))
            setTipTextColor(getColor(R.styleable.NiftySlider_tipViewTextColor, Color.BLACK))
            setTipTextAutoChange(getBoolean(R.styleable.NiftySlider_tipTextAutoChange, true))
            setTipViewClippingEnabled(getBoolean(R.styleable.NiftySlider_isTipViewClippingEnabled, false))
            setTouchMode(getInt(R.styleable.NiftySlider_sliderTouchMode, MODE_NORMAL))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isVertical()) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(viewWidth, MeasureSpec.EXACTLY),
                heightMeasureSpec
            )
        }else{
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(viewHeight, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTrackSize(w,h)
        updateHaloHotspot()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        trackPaint.color = getColorForState(trackColor)
        trackSecondaryPaint.color = getColorForState(trackSecondaryColor)
        ticksPaint.color = getColorForState(ticksColor)
        inactiveTicksPaint.color = getColorForState(ticksColorInactive)
        inactiveTrackPaint.color = getColorForState(trackColorInactive)
        if (defaultThumbDrawable.isStateful) {
            defaultThumbDrawable.state = drawableState
        }
        defaultThumbDrawable.thumbTextColor = getColorForState(thumbTextColor)
        haloPaint.color = getColorForState(haloColor)
        haloPaint.alpha = HALO_ALPHA
    }

    fun paddingVDiff() = paddingTop - paddingBottom
    fun paddingHDiff() = paddingStart - paddingEnd

    fun isVertical() = mOrientation == VERTICAL

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (hasDirtyData) {
            validateDirtyData()
        }

        val yCenter = measuredHeight / 2f + paddingVDiff() / 2f
        val xCenter = measuredWidth / 2f + paddingHDiff() / 2f
        val trackCenter = if (isVertical()) xCenter else yCenter
        val width = measuredWidth

        viewRectF.apply {
            if (isVertical()){
                set(
                    trackCenter - trackThickness / 2f,
                    0f + paddingTop + trackInnerVPadding,
                    trackCenter + trackThickness / 2f,
                    height.toFloat() - paddingBottom - trackInnerVPadding
                )
            }else{
                set(
                    0f + paddingStart + trackInnerHPadding,
                    trackCenter - trackThickness / 2f,
                    width.toFloat() - paddingEnd - trackInnerHPadding,
                    trackCenter + trackThickness / 2f
                )
            }
        }

        onDrawBefore(canvas, viewRectF, trackCenter)
        drawDebugArea(canvas, trackCenter)


        drawInactiveTrack(canvas, trackCenter)
        drawSecondaryTrack(canvas, trackCenter)
        drawTrack(canvas, trackCenter)
        drawTicks(canvas, trackCenter)

        if ((isDragging || isFocused) && isEnabled) {
            //仅在v23以下版本启用此逻辑
            drawCompatHaloIfNeed(canvas, trackCenter)
        }

        drawThumb(canvas, trackCenter)
        onDrawAfter(canvas, viewRectF, trackCenter)
    }

    override fun invalidateDrawable(drawable: Drawable) {
        super.invalidateDrawable(drawable)
        invalidate()
    }

    override fun onAttachedToWindow() {
        if (isShowTipView) {
            tipView.attachTipView(this)
        }
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        if (Utils.isActivityAlive(context)) {
            tipView.detachTipView(this)
        }
        super.onDetachedFromWindow()
    }

    private fun drawDebugArea(canvas: Canvas, yCenter: Float) {
        val offset = 1
        if (DEBUG_MODE) {
            debugPaint.color = Color.RED
            canvas.drawRect(
                0f + offset,
                0f + offset,
                canvas.width.toFloat() - offset,
                canvas.height.toFloat() - offset,
                debugPaint
            )
            debugPaint.color = Color.BLUE
            canvas.drawLine(
                0f,
                canvas.height / 2f,
                canvas.width.toFloat(),
                canvas.height / 2f,
                debugPaint
            )
            canvas.drawLine(
                canvas.width / 2f,
                0f,
                canvas.width / 2f,
                canvas.height.toFloat(),
                debugPaint
            )
            debugPaint.color = Color.GREEN
            canvas.drawLine(
                viewRectF.left,
                0f,
                viewRectF.left,
                canvas.height.toFloat(),
                debugPaint
            )
            canvas.drawLine(
                viewRectF.right,
                0f,
                viewRectF.right,
                canvas.height.toFloat(),
                debugPaint
            )
            canvas.drawLine(
                0f,
                viewRectF.top,
                canvas.width.toFloat(),
                viewRectF.top,
                debugPaint
            )
            canvas.drawLine(
                0f,
                viewRectF.bottom,
                canvas.width.toFloat(),
                viewRectF.bottom,
                debugPaint
            )

        }
    }

    /**
     * draw active track
     * 需要考虑如果使用半透明颜色时会与下层[trackColorInactive]颜色进行叠加，需注意叠加后的效果是否满足要求
     */
    private fun drawTrack(canvas: Canvas, trackCenter: Float) {

        updateTrackRect(trackCenter = trackCenter, progress = percentValue(value))

        if (!dispatchDrawTrackBefore(canvas, trackRectF, inactiveTrackRectF, trackCenter)) {

            val cornerRadius = if (trackCornerRadius == UNSET) trackThickness / 2f else trackCornerRadius.toFloat()

            if (value > valueFrom) {

                if (trackStartColor == null || trackEndColor == null) {
                    //渐变必须要开始和结束颜色,其中有一个则跳过设置画笔为渐变
                    //require start color and end color
                } else {
                    val startColor = getColorForState(trackStartColor!!)
                    val endColor = getColorForState(trackEndColor!!)
                    val colors = if (trackCenterColor == null) {
                        intArrayOf(startColor,endColor)
                    } else {
                        val centerColor = getColorForState(trackCenterColor!!)
                        intArrayOf(startColor,centerColor,endColor)
                    }

                    trackPaint.shader = LinearGradient(
                        0f, 0f, trackRectF.width(), trackRectF.height(),
                        colors,
                        null,
                        Shader.TileMode.CLAMP
                    )
                }

                canvas.drawRoundRect(
                    trackRectF,
                    cornerRadius,
                    cornerRadius,
                    trackPaint
                )
            }
        }

        drawTrackAfter(canvas, trackRectF, inactiveTrackRectF, trackCenter)
    }

    /**
     * draw secondary track
     * 需要考虑如果使用半透明颜色时会与下层[trackSecondaryColor]颜色进行叠加，需注意叠加后的效果是否满足要求
     */
    private fun drawSecondaryTrack(canvas: Canvas, trackCenter: Float) {

        updateTrackRect(trackCenter = trackCenter, progress = percentValue(secondaryValue))

        if (!dispatchDrawSecondaryTrackBefore(canvas, trackRectF,inactiveTrackRectF, trackCenter)) {
            val cornerRadius = if (trackCornerRadius == UNSET) trackThickness / 2f else trackCornerRadius.toFloat()

            if (secondaryValue > valueFrom) {
                canvas.drawRoundRect(
                    trackRectF,
                    cornerRadius,
                    cornerRadius,
                    trackSecondaryPaint
                )
            }
        }

        drawSecondaryTrackAfter(canvas, trackRectF,inactiveTrackRectF, trackCenter)
    }

    /**
     * draw inactive track
     */
    private fun drawInactiveTrack(canvas: Canvas, trackCenter: Float) {
        inactiveTrackRectF.apply {
            if (isVertical()){
                set(
                    trackCenter - trackThickness / 2f,
                    0f + paddingTop + trackInnerVPadding,
                    trackCenter + trackThickness / 2f,
                    measuredHeight.toFloat() - paddingBottom - trackInnerVPadding
                )
            }else{
                set(
                    0f + paddingStart + trackInnerHPadding,
                    trackCenter - trackThickness / 2f,
                    measuredWidth.toFloat() - paddingEnd - trackInnerHPadding,
                    trackCenter + trackThickness / 2f
                )
            }
        }

        val progress = if (isRtl() && !isVertical()) 0f else 1f
        updateTrackRect(trackCenter = trackCenter, progress = progress)

        if (!dispatchDrawInactiveTrackBefore(canvas, trackRectF, trackCenter)) {

            val cornerRadius = if (trackCornerRadius == UNSET) trackThickness / 2f else trackCornerRadius.toFloat()

            canvas.drawRoundRect(
                trackRectF,
                cornerRadius,
                cornerRadius,
                inactiveTrackPaint
            )
        }

        drawInactiveTrackAfter(canvas, trackRectF, trackCenter)
    }

    /**
     * Draw thumb
     * 在[setThumbWithinTrackBounds]模式下，thumb会向内缩进[thumbRadius]距离
     */
    private fun drawThumb(canvas: Canvas, trackCenter: Float) {

        if (!thumbAnimation.isThumbHidden()) {

            val thumbDrawable = customThumbDrawable ?: defaultThumbDrawable

            val cx = if (isVertical()) {
                trackCenter - (thumbDrawable.bounds.width() / 2f)
            } else {
                paddingStart + trackInnerHPadding + thumbOffset + (percentValue(value) * (trackWidth - thumbOffset * 2))
            }
            val cy = if (isVertical()) {
                paddingTop + trackInnerVPadding + thumbOffset + ((1 - percentValue(value)) * (trackHeight - thumbOffset * 2))
            } else {
                trackCenter - (thumbDrawable.bounds.height() / 2f) + thumbVOffset
            }

            val tx = if (isVertical()) {
                cx
            } else {
                cx - thumbDrawable.bounds.width() / 2f
            }

            val ty = if (isVertical()) {
                cy - thumbDrawable.bounds.height() / 2f
            } else {
                cy
            }


            if (!dispatchDrawThumbBefore(canvas, cx, trackCenter)) {
                canvas.withTranslation(tx, ty) {
                    thumbDrawable.draw(canvas)
                }
            }

            drawThumbAfter(canvas, cx, trackCenter)
        }
    }

    /**
     * Draw compat halo
     * 绘制滑块的光环效果
     */
    private fun drawCompatHaloIfNeed(canvas: Canvas, trackCenter: Float) {
        if (shouldDrawCompatHalo() && enableDrawHalo) {
            val cx = if (isVertical()){
                trackCenter
            }else{
                paddingStart + trackInnerHPadding + thumbOffset + percentValue(value) * (trackWidth - thumbOffset * 2)
            }
            val cy = if (isVertical()){
                paddingTop + trackInnerVPadding + thumbOffset + (1 - percentValue(value)) * (trackHeight - thumbOffset * 2)
            }else{
                trackCenter
            }
            //允许光环绘制到边界以外
            if (parent is ViewGroup) {
                (parent as ViewGroup).clipChildren = false
            }

            canvas.drawCircle(cx, cy, haloRadius.toFloat(), haloPaint)
        }
    }


    private val tickPoint = PointF()
    /**
     * draw tick
     */
    private fun drawTicks(canvas: Canvas, trackCenter: Float) {
        if (enableStepMode() && tickVisible) {

            val trackSize = if (isVertical()) trackHeight else trackWidth
            val drawSize = trackSize - thumbOffset * 2 - tickRadius * 2

            val tickCount: Int = ((valueTo - valueFrom) / stepSize + 1).toInt()
            val stepSize = drawSize / (tickCount - 1).toFloat()

            val thumbPos = if (isVertical()){
                (1 - percentValue(value)) * height + paddingTop + trackInnerVPadding + thumbOffset
            }else{
                percentValue(value) * trackWidth + paddingStart + trackInnerHPadding + thumbOffset
            }

            if (!dispatchDrawIndicatorsBefore(canvas, trackRectF, trackCenter)) {

                for (i in 0 until tickCount) {


                    val start = if (isVertical()){
                        paddingTop + trackInnerVPadding + thumbOffset + tickRadius
                    } else{
                        paddingStart + trackInnerHPadding + thumbOffset + tickRadius
                    }
                    val center = start + i * stepSize

                    var circlePaint = if (center <= thumbPos) {
                        if (isVertical()) inactiveTicksPaint else ticksPaint
                    } else {
                        if (isVertical()) ticksPaint else inactiveTicksPaint
                    }

                    tickPoint.apply {
                        if (isVertical()){
                            x = trackCenter
                            y = start + i * stepSize
                        }else{
                            x = start + i * stepSize
                            y = trackCenter
                        }
                    }

                    if (!dispatchDrawIndicatorBefore(canvas, trackRectF, tickPoint,i)) {
                        canvas.drawCircle(
                            tickPoint.x,
                            tickPoint.y,
                            tickRadius,
                            circlePaint
                        )
                    }

                    drawIndicatorAfter(canvas, trackRectF, tickPoint,i)
                }
            }

            drawIndicatorsAfter(canvas, trackRectF, trackCenter)
        }
    }


    private fun trackCompatRtl(rectF: RectF){
        if (isRtl()) {
            val right = rectF.right
            rectF.left = right
            rectF.right = width.toFloat() - paddingEnd - trackInnerHPadding
        }
    }

    private fun updateTrackRect(trackCenter: Float, progress:Float){
        val trackOffset = if(isRtl()) 0 else thumbOffset * 2
        trackRectF.apply {
            if (isVertical()){
                set(
                    trackCenter - trackThickness / 2f,
                    paddingTop + trackInnerVPadding + ((1 - progress) * (trackHeight - thumbOffset * 2)),
                    trackCenter + trackThickness / 2f,
                    paddingTop + trackInnerVPadding + trackOffset + (trackHeight - thumbOffset * 2f)
                )
            }else{
                set(
                    0f + paddingStart + trackInnerHPadding,
                    trackCenter - trackThickness / 2f,
                    paddingStart + trackInnerHPadding + trackOffset + (trackWidth - thumbOffset * 2) * progress,
                    trackCenter + trackThickness / 2f
                )
            }
        }
        if (isRtl() && !isVertical()) {
            val right = trackRectF.right
            trackRectF.left = right
            trackRectF.right = width.toFloat() - paddingEnd - trackInnerHPadding
        }
    }

    /**
     * Returns a number between 0 and 1 with [BaseSlider.value]
     * 通过value返回当前滑动百分比，0为最左、1为最右
     */
    fun percentValue(v: Float = value): Float {
        val progress = (v - valueFrom) / (valueTo - valueFrom)
        return if (isRtl()) 1 - progress else progress
    }


    /**
     * This method is called before the onDraw.make sure parameter is valid
     * 对可能存在的脏数据进行校验或修正
     */
    private fun validateDirtyData() {
        if (hasDirtyData) {
            validateValueFrom()
            validateValueTo()
            validateValue()
            updateDirtyData()
            hasDirtyData = false
        }
    }

    /**
     * 校验[valueFrom]合法性
     */
    private fun validateValueFrom() {
        if (valueFrom > valueTo) {
            throw IllegalStateException("valueFrom($valueFrom) must be smaller than valueTo($valueTo)")
        }
    }

    /**
     * 校验[valueTo]合法性
     */
    private fun validateValueTo() {
        if (valueTo <= valueFrom) {
            throw IllegalStateException("valueTo($valueTo) must be greater than valueFrom($valueFrom)")
        }
    }

    /**
     * 校验[BaseSlider.value]合法性，对不合法数据进行修正
     */
    private fun validateValue() {
        //value 超出起始结束范围则进行修正
        value = MathUtils.clamp(value, valueFrom, valueTo)
        secondaryValue = MathUtils.clamp(secondaryValue, valueFrom, valueTo)
    }

    fun updateViewLayout() {
        updateTrackSize(width,height)
        if (viewHeightChanged()) {
            requestLayout()
        } else {
            invalidate()
        }
    }


    /**
     * Returns true if view height changed
     * 检查高度是否发生变化，变化后更新当前记录高度
     */
    fun viewHeightChanged(): Boolean {
        var isChanged = false

        val leftRightPadding = paddingStart + paddingEnd
        val minWidthWithTrack = leftRightPadding + trackThickness
        val thumbWidth = customThumbDrawable?.bounds?.width() ?: defaultThumbDrawable.bounds.width()
        val minWidthWithThumb = leftRightPadding + thumbWidth + trackInnerHPadding * 2
        val tempWidth = max(minWidthWithTrack, minWidthWithThumb)
        if (tempWidth != viewWidth) {
            viewWidth = max(tempWidth, sourceViewWidth)
            isChanged = true
        }

        val topBottomPadding = paddingTop + paddingBottom
        val minHeightWithTrack = topBottomPadding + trackThickness
        val thumbHeight = customThumbDrawable?.bounds?.height() ?: defaultThumbDrawable.bounds.height()
        val minHeightWithThumb = topBottomPadding + thumbHeight + trackInnerVPadding * 2
        val tempHeight = max(minHeightWithTrack, minHeightWithThumb)
        if (tempHeight != viewHeight) {
            viewHeight = max(tempHeight, sourceViewHeight)
            isChanged = true
        }

        return isChanged
    }

    /**
     * update track real draw width
     * 更新滑轨真实绘制宽度，真实宽度不仅受左右padding影响，还会受内部[trackInnerHPadding]影响
     */
    fun updateTrackSize(w: Int,h:Int) {
        if (isVertical()){
            trackWidth = trackThickness
            trackHeight = max(h - paddingTop - paddingBottom - trackInnerVPadding * 2, 0)
        }else {
            trackWidth = max(w - paddingStart - paddingEnd - trackInnerHPadding * 2, 0)
            trackHeight = trackThickness
        }
    }


    /**
     * Sets the slider's [BaseSlider.value]
     * 如果存在step size时 value 可能会根据step size进行修正
     *
     * @param value 必须小于等于 [valueTo] 大于等于 [valueFrom]
     */
    fun setValue(value: Float, animated: Boolean = enableProgressAnim) {
        //用户滑动过程禁止改变value
        if (this.value != value && !isDragging) {
            updateValue(value, animated)
        }
    }

    private fun updateValue(value: Float, animated: Boolean = false) {
        hasDirtyData = true
        val currentValue = this.value
        if (animated) {
            val radio = (abs(value - currentValue)) / (valueTo - valueFrom)
            val duration = if (radio < 0.35) max(radio * 500f, 0f) else 300
            progressAnimator.apply {
                cancel()
                this.duration = duration.toLong()
                setFloatValues(currentValue, value)
                start()
            }

        } else {
            this.value = value
            valueChanged(value, isDragging)
            updateHaloHotspot()
            postInvalidate()
        }
    }

    /**
     * Sets the slider's [BaseSlider.secondaryValue]
     *
     * @param secondaryValue 必须小于等于 [valueTo] 大于等于 [valueFrom]
     */
    fun setSecondaryValue(secondaryValue: Float) {
        if (this.secondaryValue != secondaryValue) {
            this.secondaryValue = secondaryValue
            hasDirtyData = true
            postInvalidate()
        }
    }

    /**
     * Sets whether the auto changed horizontal inner padding when no values are set
     *
     * @see R.attr.enableAutoHPadding
     */
    fun setEnableAutoHPadding(enable: Boolean) {
        this.enableAutoHPadding = enable
    }

    /**
     * Sets the vertical inner padding of the track.
     * 主要处理thumb阴影超出部分的视图，使thumb展示正常
     *
     * @see R.attr.trackInnerVPadding
     *
     * @param padding track左右的padding值，
     */
    fun setTrackInnerVPadding(padding: Int) {
        val innerVPadding = if (padding == UNSET) {
            ceil(thumbElevation).toInt()
        } else {
            padding
        }

        if (innerVPadding == trackInnerVPadding) {
            return
        }

        trackInnerVPadding = innerVPadding
        updateViewLayout()
    }

    /**
     * Sets the horizontal inner padding of the track.
     * 主要处理thumb超出部分的视图，使thumb展示正常
     * 也可以使用 [BaseSlider.setThumbWithinTrackBounds] 来将thumb直接控制在track内部
     *
     * @see R.attr.trackInnerHPadding
     *
     * @param padding track左右的padding值，
     */
    fun setTrackInnerHPadding(padding: Int = UNSET) {
        val innerHPadding = if (padding == UNSET) {
            if (enableAutoHPadding) {
                if (isThumbWithinTrackBounds) {
                    //thumb with in track bounds 模式下只需要要考虑超出阴影视图
                    ceil(thumbElevation).toInt()
                } else {
                    thumbRadius + ceil(thumbElevation).toInt()
                }
            } else {
                0
            }

        } else {
            padding
        }

        if (innerHPadding == trackInnerHPadding) {
            return
        }

        trackInnerHPadding = innerHPadding
        updateViewLayout()
    }


    /**
     * Sets the radius of the track corners.
     *
     * 设置滑轨转角圆角值
     * @see R.attr.trackCornersRadius
     *
     * @param radius 圆角半径
     */
    fun setTrackCornersRadius(@IntRange(from = 0) @Dimension radius: Int) {
        if (radius == trackCornerRadius) {
            return
        }
        trackCornerRadius = radius
        postInvalidate()
    }

    /**
     * Sets the color for the track
     *
     * @see R.attr.trackColor
     */
    fun setTrackTintList(color: ColorStateList) {
        if (this::trackColor.isInitialized && color == trackColor) {
            return
        }
        trackColor = color
        trackPaint.color = getColorForState(trackColor)
        invalidate()
    }

    /**
     * Sets the color for the secondary track
     *
     * @see R.attr.trackSecondaryColor
     *
     * eg.视频滑动进度条可能存在缓存进度,通过此方法来改变二级滑轨颜色
     */
    fun setTrackSecondaryTintList(color: ColorStateList) {
        if (this::trackSecondaryColor.isInitialized && color == trackSecondaryColor) {
            return
        }
        trackSecondaryColor = color
        trackSecondaryPaint.color = getColorForState(trackSecondaryColor)
        invalidate()
    }

    /**
     * Sets the inactive color for the track
     *
     * @see R.attr.trackColorInactive
     */
    fun setTrackInactiveTintList(color: ColorStateList) {
        if (this::trackColorInactive.isInitialized && color == trackColorInactive) {
            return
        }
        trackColorInactive = color
        inactiveTrackPaint.color = getColorForState(trackColorInactive)
        invalidate()
    }

    /**
     * Sets the color for the tick
     *
     * @see R.attr.ticksColor
     */
    fun setTicksTintList(color: ColorStateList) {
        if (this::ticksColor.isInitialized && color == ticksColor) {
            return
        }
        ticksColor = color
        ticksPaint.color = getColorForState(ticksColor)
        invalidate()
    }

    /**
     * Sets the inactive color for the tick
     *
     * @see R.attr.ticksColorInactive
     */
    fun setTicksInactiveTintList(color: ColorStateList) {
        if (this::ticksColorInactive.isInitialized && color == ticksColorInactive) {
            return
        }
        ticksColorInactive = color
        inactiveTicksPaint.color = getColorForState(ticksColorInactive)
        invalidate()
    }

    /**
     * Sets the radius of the tick in pixels.
     * 设置刻度半径大小
     *
     * @see R.attr.tickRadius
     */
    fun setTickRadius(@FloatRange(from = 0.0) @Dimension tickRadius: Float) {
        if (this.tickRadius != tickRadius) {
            this.tickRadius = tickRadius
            postInvalidate()
        }
    }

    /**
     * Sets the text of the thumb
     *
     * @see R.attr.thumbText
     */
    fun setThumbText(text: String?) {
        if (defaultThumbDrawable.thumbText != text) {
            defaultThumbDrawable.thumbText = text
            postInvalidate()
        }
    }

    /**
     * Sets the icon of the thumb
     *
     * @see R.attr.thumbIcon
     */
    fun setThumbIcon(icon: Drawable?) {
        if (defaultThumbDrawable.thumbIcon != icon) {
            defaultThumbDrawable.thumbIcon = icon
            postInvalidate()
        }
    }

    /**
     * Sets the icon size of the thumb
     *
     * @see R.attr.thumbIconSize
     */
    fun setThumbIconSize(size: Int) {
        if (defaultThumbDrawable.thumbIconSize != size) {
            defaultThumbDrawable.thumbIconSize = size
            postInvalidate()
        }
    }

    /**
     * Sets the icon tint color of the thumb
     *
     * @see R.attr.thumbIconTintColor
     */
    fun setThumbIconTintColor(color: Int) {
        if (defaultThumbDrawable.thumbIconTintColor != color) {
            defaultThumbDrawable.thumbIconTintColor = color
            postInvalidate()
        }
    }

    /**
     * Sets the radius of the thumb in pixels.
     * 设置滑块半径大小
     * 如果使用自定义drawable时为长边半径
     *
     * @see R.attr.thumbRadius
     *
     * @param radius 滑块半径
     */
    var thumbRadius = 0
        set(@IntRange(from = 0) @Dimension radius) {
            if (field == radius) {
                return
            }
            field = radius
            this.thumbWidth = radius * 2
            this.thumbHeight = radius * 2
            defaultThumbDrawable.cornerSize = radius.toFloat()
            adjustThumbDrawableBounds(thumbWidth, thumbHeight)
            updateViewLayout()
        }


    /**
     * Sets the width and height of the thumb.this conflicts with the [thumbRadius]
     * 设置滑块宽高
     * 不适用于自定义thumb drawable
     *
     * @see R.attr.thumbWidth
     * @see R.attr.thumbHeight
     *
     * @param radius 滑块半径
     */
    fun setThumbWidthAndHeight(thumbWidth: Int, thumbHeight: Int, radius: Int = thumbRadius) {
        if ((this.thumbWidth == thumbWidth && this.thumbHeight == thumbHeight) || (thumbHeight < 0 && thumbWidth <= 0)) {
            return
        }
        if (thumbWidth >= 0) {
            this.thumbWidth = thumbWidth
        } else {
            this.thumbWidth = thumbRadius * 2
        }

        if (thumbHeight >= 0) {
            this.thumbHeight = thumbHeight
        } else {
            this.thumbHeight = thumbRadius * 2
        }

        if (radius != thumbRadius) {
            defaultThumbDrawable.cornerSize = radius.toFloat()
        }

        defaultThumbDrawable.setBounds(
            0,
            0,
            this.thumbWidth,
            this.thumbHeight
        )
        updateViewLayout()
    }


    /**
     * Sets the vertical offset of the thumb
     * 设置thumb纵向的偏移量
     *
     * @see R.attr.thumbVOffset
     *
     * @param offset 偏移量
     */
    fun setThumbVOffset(offset: Int) {
        if (offset == thumbVOffset) {
            return
        }
        thumbVOffset = offset
        postInvalidate()
    }

    /**
     * Sets whether the thumb within track bounds
     * 正常模式下滑块thumb是以track的起始位置为中心,thumb较大时左半部分会超出视图边界
     * 某些样式下，thumb需要控制在track的范围以内，可通过此方法来启用此项功能
     *
     * @see R.attr.thumbWithinTrackBounds
     *
     * @param isInBounds thumb 是否需要绘制在 track 范围以内
     */
    fun setThumbWithinTrackBounds(isInBounds: Boolean) {

        isThumbWithinTrackBounds = isInBounds

        val offset = if (isInBounds) {
            //启用状态下直接使用thumb的半径做为向内偏移的具体数值
            if (isVertical()) {
                if (thumbHeight != UNSET) thumbHeight / 2 else thumbRadius
            } else {
                if (thumbWidth != UNSET) thumbWidth / 2 else thumbRadius
            }
        } else {
            0
        }

        if (thumbOffset == offset) {
            return
        }
        thumbOffset = offset
        setTrackInnerHPadding()
        updateViewLayout()
    }

    /**
     * Sets the color of the thumb.
     *
     * @see R.attr.thumbColor
     */
    fun setThumbTintList(thumbColor: ColorStateList) {
        if (thumbColor == defaultThumbDrawable.fillColor) {
            return
        }
        defaultThumbDrawable.fillColor = thumbColor
        invalidate()
    }

    /**
     * Sets the color of the thumb text.
     *
     * @see R.attr.thumbTextColor
     */
    fun setThumbTextTintList(color: ColorStateList?) {
        if (color != null) {
            if (this::thumbTextColor.isInitialized && thumbTextColor == color) {
                return
            }
            thumbTextColor = color
            defaultThumbDrawable.thumbTextColor = getColorForState(thumbTextColor)
            invalidate()
        }
    }

    /**
     * Sets the text size of the thumb text.
     *
     * @see R.attr.thumbTextSize
     */
    fun setThumbTextSize(size: Float) {
        if (defaultThumbDrawable.thumbTextSize != size) {
            defaultThumbDrawable.thumbTextSize = size
            invalidate()
        }
    }

    fun setThumbTextBold(isBold: Boolean) {
        if (defaultThumbDrawable.isThumbTextBold != isBold) {
            defaultThumbDrawable.isThumbTextBold = isBold
            invalidate()
        }
    }


    fun setThumbCustomDrawable(@DrawableRes drawableResId: Int) {
        ContextCompat.getDrawable(context, drawableResId)?.also {
            setThumbCustomDrawable(it)
        }
    }

    fun setThumbCustomDrawable(drawable: Drawable) {
        customThumbDrawable = initializeCustomThumbDrawable(drawable)
        postInvalidate()
    }


    /**
     * Sets the color of the halo.
     * 设置滑块点击后光环颜色
     *
     * @see R.attr.haloColor
     */
    fun setHaloTintList(haloColor: ColorStateList) {
        if (this::haloColor.isInitialized && this.haloColor == haloColor) {
            return
        }

        this.haloColor = haloColor
        //v23以下通过绘制实现，仅修改画笔颜色即可
        if (!shouldDrawCompatHalo() && background is RippleDrawable) {
            (background as RippleDrawable).setColor(haloColor)
            return
        }

        haloPaint.apply {
            color = getColorForState(haloColor)
            alpha = HALO_ALPHA
        }

        invalidate()

    }

    /**
     * Sets the radius of the halo in pixels.
     * 设置滑块点击后光环的半径
     *
     * @see R.attr.haloRadius
     */
    fun setHaloRadius(@IntRange(from = 0) @Dimension radius: Int) {
        if (haloRadius == radius) {
            return
        }

        haloRadius = radius
        //v23以下通过绘制实现，v23以上通过hook ripple effect background来修改半径
        if (!shouldDrawCompatHalo() && enableDrawHalo && background is RippleDrawable) {
            hookRippleRadius(background as RippleDrawable, haloRadius)
            return
        }
        postInvalidate()
    }


    /**
     * Sets the elevation of the thumb.
     *
     * @see R.attr.thumbElevation
     */
    fun setThumbElevation(elevation: Float) {
        if (elevation > 0) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        defaultThumbDrawable.elevation = elevation
        thumbElevation = elevation
        postInvalidate()
    }

    /**
     * Sets the stroke color for the thumbs
     *
     * @see R.attr.thumbStrokeColor
     */
    fun setThumbStrokeColor(thumbStrokeColor: ColorStateList?) {
        defaultThumbDrawable.strokeColor = thumbStrokeColor
        postInvalidate()
    }

    /**
     * Sets the stroke width for the thumb
     *
     * @see R.attr.thumbStrokeWidth
     */
    fun setThumbStrokeWidth(thumbStrokeWidth: Float) {
        defaultThumbDrawable.strokeWidth = thumbStrokeWidth
        postInvalidate()
    }

    /**
     * Sets the shadow width for the thumb
     *
     * @see R.attr.thumbShadowColor
     */
    fun setThumbShadowColor(@ColorInt shadowColor: Int) {
        defaultThumbDrawable.shadowColor = shadowColor
    }


    /**
     * Sets whether the halo should be draw
     * 启用光环效果
     *
     * @see R.attr.enableDrawHalo
     *
     * @param enable True if this enable draw halo
     */
    fun setEnableDrawHalo(enable: Boolean) {
        enableDrawHalo = enable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && haloDrawable == null && enable) {
            background = ContextCompat.getDrawable(context, R.drawable.halo_background)
            haloDrawable = background as RippleDrawable
        }
    }

    /**
     * Sets whether the tip view are visible
     *
     * @see R.attr.tipViewVisible
     */
    fun setTipViewVisibility(visibility: Boolean) {
        if (isShowTipView == visibility) {
            return
        }
        isShowTipView = visibility
        if (visibility) {
            tipView.attachTipView(this)
        }
    }

    /**
     * Sets the tip view vertical offset
     *
     * Default is thumb radius + [TipViewContainer.defaultSpace]
     */
    fun setTipVerticalOffset(offset: Int) {
        if (offset != 0) {
            tipView.verticalOffset = offset
        }
    }

    /**
     * Sets the tip view background color
     *
     * @see R.attr.tipViewBackground
     */
    fun setTipBackground(@ColorInt color: Int) {
        tipView.setTipBackground(color)
    }

    /**
     * Sets the tip view text color
     *
     * @see R.attr.tipViewTextColor
     */
    fun setTipTextColor(@ColorInt color: Int) {
        tipView.setTipTextColor(color)
    }


    /**
     * Sets the tip text auto change
     *
     * @see R.attr.tipTextAutoChange
     */
    fun setTipTextAutoChange(isAutoChange: Boolean) {
        tipView.isTipTextAutoChange = isAutoChange
    }

    /**
     *  Sets whether the tip view will be fully within the bounds
     *
     *  是否将tip view 始终限制在屏幕内 ，默认为 false , tip view将根据滑块位置来计算真实位置，可能会移动到屏幕外
     *
     *  @see R.attr.isTipViewClippingEnabled
     */
    fun setTipViewClippingEnabled(enable: Boolean) {
        tipView.isClippingEnabled = enable
    }


    /**
     * Sets the slider touch mode
     *  - [BaseSlider.MODE_NORMAL]
     *  - [BaseSlider.MODE_DISABLE_TOUCH]
     *  - [BaseSlider.MODE_DISABLE_CLICK_TOUCH]
     *
     * @see R.attr.sliderTouchMode
     */
    fun setTouchMode(mode: Int) {
        this.sliderTouchMode = mode
    }


    /**
     * Add a custom tip view
     */
    fun addCustomTipView(view: View) {
        tipView.customTipView = view
    }

    /**
     * Create tip view show/hide animation
     */
    fun createTipAnimation(animator: TipViewAnimator) {
        tipView.animator = animator
    }

    /**
     * Returns true if step mode enable
     * 是否启用了刻度功能
     */
    fun enableStepMode(): Boolean {
        return stepSize > 0
    }

    /**
     * Update halo Hotspot coordinate
     *
     * 仅在v23及以上生效，更新ripple effect坐标
     */
    fun shouldDrawCompatHalo(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || background !is RippleDrawable
    }


    @ColorInt
    fun getColorForState(colorStateList: ColorStateList): Int {
        return colorStateList.getColorForState(drawableState, colorStateList.defaultColor)
    }

    /**
     * Get the thumb center x-coordinates
     */
    fun getThumbCenterX(): Float {
        return if (isVertical()) {
            measuredWidth / 2f + paddingHDiff() / 2f
        } else {
            paddingStart + trackInnerHPadding + thumbOffset + (percentValue(value) * (trackWidth - thumbOffset * 2))
        }
    }

    /**
     * Get the thumb center y-coordinates
     */
    fun getThumbCenterY(): Float {
        return if (isVertical()) {
            paddingTop + trackInnerVPadding + thumbOffset + ((1 - percentValue(value)) * (trackHeight - thumbOffset * 2))
        } else {
            measuredHeight / 2f + paddingVDiff() / 2f + thumbVOffset
        }
    }

    /**
     * show thumb on sliders
     *
     * @param animated Whether to update the thumb visibility with the animation
     * @param delayMillis The delay the show thumb Runnable will be executed
     */
    fun showThumb(animated: Boolean = true, delayMillis: Long = 0) {
        thumbAnimation.show(animated, delayMillis)
    }

    /**
     * hide thumb on sliders
     *
     * @param animated Whether to update the thumb visibility with the animation
     * @param delayMillis The delay the hide thumb Runnable will be executed
     */
    fun hideThumb(animated: Boolean = true, delayMillis: Long = 0) {
        thumbAnimation.hide(animated, delayMillis)
    }


    fun toggleThumbVisibility(animated: Boolean = true) {
        thumbAnimation.toggle(animated)
    }

    /**
     * 如果启用了刻度功能，对当前滑动坐标进行修改，定位到临近刻度
     */
    private fun snapStepPos(pos: Float): Float {
        if (enableStepMode()) {
            val stepCount = ((valueTo - valueFrom) / stepSize).toInt()
            return (pos * stepCount).roundToInt() / stepCount.toFloat()
        }
        return pos
    }

    /**
     * 通过当前滑动位置百分比转化为准确的value值
     * 起始、结束值可能存在多种类型
     *
     * eg.
     * valueFrom = -40  valueTo = -20
     * valueFrom = -1  valueTo = 1
     * valueFrom = 1  valueTo = 100
     * valueFrom = 50  valueTo = 80
     *
     */
    private fun getValueByTouchPos(pos: Float): Float {
        val position = snapStepPos(pos)
        return position * (valueTo - valueFrom) + valueFrom
    }


    /**
     * Get the sliding position percentage based on the current x-coordinates
     */
    private fun getTouchPosByX(touchX: Float): Float {
        val progress = MathUtils.clamp((touchX - paddingStart - trackInnerHPadding) / trackWidth, 0f, 1f)
        return if(isRtl()) 1- progress else progress
    }

    /**
     * Get the sliding position percentage based on the current y-coordinates
     */
    private fun getTouchPosByY(touchY: Float): Float {
        val progress = MathUtils.clamp((touchY - paddingTop - trackInnerVPadding) / trackHeight, 0f, 1f)
        return 1 - progress
    }

    /**
     * Get x-coordinates based on progress
     */
    private fun getCoordinateXByValue(value:Float):Float{
        return (paddingStart + trackInnerHPadding + trackWidth * ((value - valueFrom)/(valueTo - valueFrom)))
    }

    /**
     * Get y-coordinates based on progress
     */
    private fun getCoordinateYByValue(value:Float):Float{
        return (paddingTop + trackInnerVPadding + trackHeight * (1f- (value - valueFrom)/(valueTo - valueFrom)))
    }

    /**
     * Get the current progress value by the touch position
     */
    private fun getTouchValue(x: Float, y: Float): Float {
        val touchPos = if (isVertical()) getTouchPosByY(y) else getTouchPosByX(x)
        val touchValue = getValueByTouchPos(touchPos)
        return touchValue
    }

    /**
     * 是否在纵向滚动的容器中
     * 此情况下需要对touch event做特殊处理
     */
    private fun isInVerticalScrollingContainer(): Boolean {
        var p = parent
        while (p is ViewGroup) {
            val parent = p
            val canScrollVertically = parent.canScrollVertically(1) || parent.canScrollVertically(-1)
            if (canScrollVertically && parent.shouldDelayChildPressedState()) {
                return true
            }
            p = p.getParent()
        }
        return false
    }


    private fun initializeCustomThumbDrawable(originalDrawable: Drawable): Drawable? {
        val drawable = originalDrawable.mutate()
        if (drawable != null) {
            adjustCustomThumbDrawableBounds(drawable)
        }
        return drawable
    }


    private fun adjustThumbDrawableBounds(width: Int, height: Int) {
        defaultThumbDrawable.setBounds(
            0,
            0,
            width,
            height
        )

        customThumbDrawable?.let {
            adjustCustomThumbDrawableBounds(it, width, height)
        }
    }


    private fun adjustCustomThumbDrawableBounds(
        drawable: Drawable,
        width: Int = thumbWidth,
        height: Int = thumbHeight
    ) {
        val originalWidth = drawable.intrinsicWidth
        val originalHeight = drawable.intrinsicHeight
        if (originalWidth == UNSET && originalHeight == UNSET) {
            drawable.setBounds(0, 0, width, height)
        } else {
            val scaleRatio = max(width, height).toFloat() / max(originalWidth, originalHeight)
            drawable.setBounds(
                0, 0, (originalWidth * scaleRatio).toInt(), (originalHeight * scaleRatio).toInt()
            )
        }
    }


    /**
     * Start drag slider
     */
    private fun startTacking(event: MotionEvent) {
        isTackingStart = true
        onStartTacking()
        tipView.show()
    }

    /**
     * stop drag slider
     */
    private fun stopTacking(event: MotionEvent) {
        if (isTackingStart) {
            onStopTacking()
        }
        isTackingStart = false
        tipView.hide()
        invalidate()
    }

    private fun valueChanged(value: Float, fromUser: Boolean, touchX: Float = 0f, touchRawX: Float = 0f) {
        onValueChanged(value, fromUser)
        tipView.onLocationChanged(getThumbCenterX(), getThumbCenterY(), value)

    }


    private fun updateHaloHotspot() {
        if (enableDrawHalo) {
            if (!shouldDrawCompatHalo() && measuredWidth > 0) {
                if (background is RippleDrawable) {
                    val haloX = if (isVertical()){
                        viewWidth / 2 + paddingHDiff()/2
                    }else{
                        (paddingStart + trackInnerHPadding + thumbOffset + (percentValue(value) * (trackWidth - thumbOffset * 2)).toInt())
                    }

                    val haloY = if (isVertical()){
                        (paddingTop + trackInnerVPadding + thumbOffset + ((1 - percentValue(value)) * (trackHeight - thumbOffset * 2)).toInt())
                    }else {
                        viewHeight / 2 + paddingVDiff() / 2
                    }

                    DrawableCompat.setHotspotBounds(
                        background,
                        haloX - haloRadius,
                        haloY - haloRadius,
                        haloX + haloRadius,
                        haloY + haloRadius
                    )
                }
            }
        }
    }


    private fun trackTouchEvent(event: MotionEvent) {
        val touchValue = if (isConsecutiveProgress) {
            getTouchValue(event.x - touchDownDiff,event.y - touchDownDiff)
        } else {
            getTouchValue(event.x,event.y)
        }

        if (this.value != touchValue) {
            val animated = event.action != MotionEvent.ACTION_MOVE && enableProgressAnim
            updateValue(touchValue, animated)
        }
    }

    /**
     * Invoked when the progress changes animation has ended
     */
    open fun onProgressAnimEnd() {

    }


    /**
     * Returns whether this Slider is enable user touch
     */
    private fun enableTouch(): Boolean {
        return isEnabled && sliderTouchMode != MODE_DISABLE_TOUCH
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enableTouch()) {
            //Users may change the enabled state of this Slider during the dragging process
            if (isDragging) {
                isDragging = false
                stopTacking(event)
            }
            return false
        }

        val currentX = event.x
        val currentY = event.y

        //Disable progress change via clicks
        val disableClickTouch = sliderTouchMode == MODE_DISABLE_CLICK_TOUCH

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = currentX
                touchDownY = currentY

                touchDownDiff = if (isVertical()){
                    touchDownY - getCoordinateYByValue(this.value)
                }else{
                    touchDownX - getCoordinateXByValue(this.value)
                }
                if (isInVerticalScrollingContainer()) {
                    //在纵向滑动布局中不处理down事件，优先外层滑动
                } else {
                    parent.requestDisallowInterceptTouchEvent(true)
                    requestFocus()
                    if (!disableClickTouch) {
                        isDragging = true
                        startTacking(event)
                        trackTouchEvent(event)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val isInvalidMove = if (isVertical()){
                    abs(currentY - touchDownY) < scaledTouchSlop
                }else{
                    abs(currentX - touchDownX) < scaledTouchSlop
                }

                if (isInvalidMove && disableClickTouch && !isDragging) {
                    //Do nothing in MODE_DISABLE_CLICK_TOUCH mode
                } else {

                    if (!isDragging) {
                        if (isInVerticalScrollingContainer() && isInvalidMove) {
                            return false
                        }
                        parent.requestDisallowInterceptTouchEvent(true)
                        startTacking(event)
                    }

                    if (abs(currentX - touchDownX) > scaledTouchSlop) {
                        progressAnimator.cancel()
                    }

                    isDragging = true
                    trackTouchEvent(event)
                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                var ignoreEvent = false
                lastTouchEvent?.let {
                    if (it.action == MotionEvent.ACTION_DOWN && isClickTouch(it, event)) {
                        if (disableClickTouch) {
                            ignoreEvent = true
                        } else {
                            startTacking(event)
                            trackTouchEvent(event)
                        }
                    }
                }

                if (!ignoreEvent) {
                    stopTacking(event)
                }

            }
        }

        isPressed = isDragging
        lastTouchEvent = MotionEvent.obtain(event)
        return true
    }


    /**
     * Returns true if current touch event is click event
     *
     * @param startEvent 滑动过程的down事件
     * @param endEvent   滑动过程的up事件
     */
    private fun isClickTouch(startEvent: MotionEvent, endEvent: MotionEvent): Boolean {
        val differenceX = abs(startEvent.x - endEvent.x)
        val differenceY = abs(startEvent.y - endEvent.y)
        return !(differenceX > scaledTouchSlop || differenceY > scaledTouchSlop)
    }

    private fun hookRippleRadius(drawable: RippleDrawable, radius: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            drawable.radius = radius
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val setMaxRadiusMethod =
                    RippleDrawable::class.java.getDeclaredMethod("setMaxRadius", Int::class.javaPrimitiveType)
                setMaxRadiusMethod.invoke(drawable, radius)
            } catch (e: NoSuchMethodException) {
                throw IllegalStateException("Couldn't set RippleDrawable radius", e)
            } catch (e: InvocationTargetException) {
                throw IllegalStateException("Couldn't set RippleDrawable radius", e)
            } catch (e: IllegalAccessException) {
                throw IllegalStateException("Couldn't set RippleDrawable radius", e)
            }
        }
    }


    fun isRtl(): Boolean {
        return layoutDirection == LAYOUT_DIRECTION_RTL
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val sliderState = SavedState(superState)
        sliderState.value = value
        sliderState.secondaryValue = secondaryValue
        return sliderState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val sliderState = state as SavedState
        super.onRestoreInstanceState(sliderState.superState)
        value = sliderState.value
        secondaryValue = sliderState.secondaryValue
    }


    internal class SavedState : BaseSavedState {
        var value = 0f
        var secondaryValue = 0f

        constructor(superState: Parcelable?) : super(superState) {}

        constructor(parcel: Parcel) : super(parcel) {
            value = parcel.readFloat()
            secondaryValue = parcel.readFloat()
        }


        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeFloat(value)
            parcel.writeFloat(secondaryValue)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }

    }


}