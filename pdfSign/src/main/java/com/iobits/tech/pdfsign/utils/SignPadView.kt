package com.iobits.tech.pdfsign.utils


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.iobits.tech.pdfsign.R
import java.io.ByteArrayOutputStream
import kotlin.math.max

class SignatureView(
    context: Context, attrs: AttributeSet?
) : View(context, attrs) {
    private var canvasBmp: Canvas? = null
    private var ignoreTouch = false
    private var previousPoint: Point? = null
    private var startPoint: Point? = null
    private var currentPoint: Point? = null
    private var lastVelocity = 0f
    private var lastWidth = 0f
    private val paint: Paint
    private val paintBm: Paint
    private var bmp: Bitmap? = null
    private var layoutLeft = 0
    private var layoutTop = 0
    private var layoutRight = 0
    private var layoutBottom = 0
    private var drawViewRect: Rect? = null
    private var penColor = 0
    private var backgroundColor = 0
    /**
     * Check if drawing on canvas is enabled or disabled
     *
     * @return boolean
     */
    /**
     * Enable or disable drawing on canvas
     *
     * @param enableSignature boolean
     */
    var isEnableSignature: Boolean = false
    /**
     * Get stoke size for signature creation
     *
     * @return float
     */
    /**
     * Set stoke size for signature creation
     *
     * @param penSize float
     */
    var penSize: Float = 0f

    init {
        this.setWillNotDraw(false)
        this.isDrawingCacheEnabled = true

        val typedArray = context.theme.obtainStyledAttributes(
            attrs, R.styleable.signature, 0, 0
        )

        try {
            backgroundColor = typedArray.getColor(
                R.styleable.signature_backgroundColor, context.resources.getColor(R.color.white)
            )
            penColor = typedArray.getColor(
                R.styleable.signature_penColor, context.resources.getColor(R.color.penRoyalBlue)
            )
            penSize = typedArray.getDimension(
                R.styleable.signature_penSize, context.resources.getDimension(R.dimen.pen_size)
            )
            isEnableSignature = typedArray.getBoolean(R.styleable.signature_enableSignature, true)
        } finally {
            typedArray.recycle()
        }

        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = penColor
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = penSize

        paintBm = Paint(Paint.ANTI_ALIAS_FLAG)
        paintBm.isAntiAlias = true
        paintBm.style = Paint.Style.STROKE
        paintBm.strokeJoin = Paint.Join.ROUND
        paintBm.strokeCap = Paint.Cap.ROUND
        paintBm.color = Color.BLACK
    }

    /**************** Getter/Setter  */

    /**
     * Get stoke color for signature creation
     *
     * @return int
     */
    fun getPenColor(): Int {
        return penColor
    }

    /**
     * Set stoke color for signature creation
     *
     * @param penColor int
     */
    fun setPenColor(penColor: Int) {
        this.penColor = penColor
        paint.color = penColor
    }

    /**
     * Get background color
     *
     * @return int
     */
    fun getBackgroundColor(): Int {
        return backgroundColor
    }


    /**
     * Set background color
     *
     * @param backgroundColor int
     */
    override fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }

    /**
     * Clear signature from canvas
     */
    fun clearCanvas() {
        previousPoint = null
        startPoint = null
        currentPoint = null
        lastVelocity = 0f
        lastWidth = 0f

        newBitmapCanvas(layoutLeft, layoutTop, layoutRight, layoutBottom)
        postInvalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutLeft = left
        layoutTop = top
        layoutRight = right
        layoutBottom = bottom
        if (bmp == null) {
            newBitmapCanvas(layoutLeft, layoutTop, layoutRight, layoutBottom)
        } else if (changed) {
            resizeBitmapCanvas(bmp!!, layoutLeft, layoutTop, layoutRight, layoutBottom)
        }
    }

    private fun newBitmapCanvas(left: Int, top: Int, right: Int, bottom: Int) {
        bmp = null
        canvasBmp = null
        if ((right - left) > 0 && (bottom - top) > 0) {
            bmp = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            canvasBmp = Canvas(bmp!!)
            canvasBmp!!.drawColor(backgroundColor)
        }
    }

    private fun resizeBitmapCanvas(bmp: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        val newBottom = max(bottom.toDouble(), bmp.height.toDouble()).toInt()
        val newRight = max(right.toDouble(), bmp.width.toDouble()).toInt()
        newBitmapCanvas(left, top, newRight, newBottom)
        canvasBmp!!.drawBitmap(bmp, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnableSignature) {
            return false
        }

        if (event.pointerCount > 1) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                ignoreTouch = false
                drawViewRect = Rect(
                    this.left, this.top, this.right, this.bottom
                )
                onTouchDownEvent(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> if (!drawViewRect!!.contains(
                    left + event.x.toInt(), this.top + event.y.toInt()
                )
            ) {
                //You are out of drawing area
                if (!ignoreTouch) {
                    ignoreTouch = true
                    onTouchUpEvent(event.x, event.y)
                }
            } else {
                //You are in the drawing area
                if (ignoreTouch) {
                    ignoreTouch = false
                    onTouchDownEvent(event.x, event.y)
                } else {
                    onTouchMoveEvent(event.x, event.y)
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> onTouchUpEvent(event.x, event.y)
            else -> {}
        }
        return true // super.onTouchEvent(event);
    }

    private fun onTouchDownEvent(x: Float, y: Float) {
        previousPoint = null
        startPoint = null
        currentPoint = null
        lastVelocity = 0f
        lastWidth = penSize

        currentPoint = Point(x, y, System.currentTimeMillis())
        previousPoint = currentPoint
        startPoint = previousPoint
        postInvalidate()
    }

    private fun onTouchMoveEvent(x: Float, y: Float) {
        if (previousPoint == null) {
            return
        }
        startPoint = previousPoint
        previousPoint = currentPoint
        currentPoint = Point(x, y, System.currentTimeMillis())

        var velocity: Float? = previousPoint?.let { currentPoint?.velocityFrom(it) }
        velocity = VELOCITY_FILTER_WEIGHT * velocity!! + (1 - VELOCITY_FILTER_WEIGHT) * lastVelocity

        val strokeWidth = getStrokeWidth(velocity)
        drawLine(lastWidth, strokeWidth, velocity)

        lastVelocity = velocity
        lastWidth = strokeWidth

        postInvalidate()
    }

    private fun onTouchUpEvent(x: Float, y: Float) {
        if (previousPoint == null) {
            return
        }
        startPoint = previousPoint
        previousPoint = currentPoint
        currentPoint = Point(x, y, System.currentTimeMillis())

        drawLine(lastWidth, 0f, lastVelocity)
        postInvalidate()
    }

    private fun getStrokeWidth(velocity: Float): Float {
        return penSize - (velocity * STROKE_DES_VELOCITY)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bmp!!, 0f, 0f, paintBm)
    }

    private fun drawLine(
        lastWidth: Float, currentWidth: Float, velocity: Float
    ) {
        val mid1 = midPoint(previousPoint, startPoint)
        val mid2 = midPoint(currentPoint, previousPoint)

        draw(
            mid1, previousPoint, mid2, lastWidth, currentWidth, velocity
        )
    }

    private fun getPt(n1: Float, n2: Float, perc: Float): Float {
        val diff = n2 - n1
        return n1 + (diff * perc)
    }

    private fun draw(
        p0: Point, p1: Point?, p2: Point, lastWidth: Float, currentWidth: Float, velocity: Float
    ) {
        if (canvasBmp != null) {
            var xa: Float
            var xb: Float
            var ya: Float
            var yb: Float
            var x: Float
            var y: Float
            val increment = if (velocity > MIN_VELOCITY_BOUND && velocity < MAX_VELOCITY_BOUND) {
                DRAWING_CONSTANT - (velocity * INCREMENT_CONSTANT)
            } else {
                MIN_INCREMENT
            }

            var i = 0f
            while (i < 1f) {
                xa = getPt(p0.x.toFloat(), p1!!.x.toFloat(), i)
                ya = getPt(p0.y.toFloat(), p1.y.toFloat(), i)
                xb = getPt(p1.x.toFloat(), p2.x.toFloat(), i)
                yb = getPt(p1.y.toFloat(), p2.y.toFloat(), i)

                x = getPt(xa, xb, i)
                y = getPt(ya, yb, i)

                val strokeVal = lastWidth + (currentWidth - lastWidth) * (i)
                paint.strokeWidth = if (strokeVal < MIN_PEN_SIZE) MIN_PEN_SIZE else strokeVal
                canvasBmp!!.drawPoint(x, y, paint)
                i += increment
            }
        }
    }

    private fun midPoint(p1: Point?, p2: Point?): Point {
        return Point((p1!!.x + p2!!.x) / 2.0f, (p1.y + p2.y) / 2, (p1.time + p2.time) / 2)
    }

    val signatureBitmap: Bitmap?
        /**
         * Get signature as bitmap
         *
         * @return Bitmap
         */
        get() = if (bmp != null) {
            Bitmap.createScaledBitmap(bmp!!, bmp!!.width, bmp!!.height, true)
        } else {
            null
        }

    private fun getSignatureBitmap(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, bitmap.width, bitmap.height, true)
    }

    /**
     * Render bitmap in signature
     *
     * @param bitmap Bitmap
     */
    fun setBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            bmp = bitmap
            canvasBmp = Canvas(bitmap)
            postInvalidate()
        }
    }

    val isBitmapEmpty: Boolean
        /**
         * Check is signature bitmap empty
         *
         * @return boolean
         */
        get() {
            if (bmp != null) {
                val emptyBitmap = Bitmap.createBitmap(
                    bmp!!.width, bmp!!.height, bmp!!.config
                )
                val canvasBmp = Canvas(emptyBitmap)
                canvasBmp.drawColor(backgroundColor)
                if (bmp!!.sameAs(emptyBitmap)) {
                    return true
                }
            }
            return false
        }


    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState, bmp)
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        val ss = state
        super.onRestoreInstanceState(ss.superState)
        setBitmap(ss.bitmap)
    }

    internal class SavedState : BaseSavedState {
        var bitmap: Bitmap?

        constructor(superState: Parcelable?, bitmap: Bitmap?) : super(superState) {
            this.bitmap = bitmap
        }

        private constructor(`in`: Parcel) : super(`in`) {
            bitmap = deCompress(`in`.createByteArray())
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeByteArray(compress(bitmap))
        }

        companion object {
            private fun compress(bitmap: Bitmap?): ByteArray {
                val stream = ByteArrayOutputStream()
                bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
                return stream.toByteArray()
            }

            private fun deCompress(byteArray: ByteArray?): Bitmap {
                return BitmapFactory.decodeByteArray(byteArray, 0, byteArray!!.size)
            }
        }
    }

    companion object {
        val TAG: String = SignatureView::class.java.simpleName
        const val MIN_PEN_SIZE: Float = 1f
        private const val MIN_INCREMENT = 0.01f
        private const val INCREMENT_CONSTANT = 0.0005f
        private const val DRAWING_CONSTANT = 0.0085f
        const val MAX_VELOCITY_BOUND: Float = 15f
        private const val MIN_VELOCITY_BOUND = 1.6f
        private const val STROKE_DES_VELOCITY = 1.0f
        private const val VELOCITY_FILTER_WEIGHT = 0.2f
    }
}