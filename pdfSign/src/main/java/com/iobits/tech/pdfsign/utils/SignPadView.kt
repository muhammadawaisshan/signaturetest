package com.iobits.tech.pdfsign.utils

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
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

class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val STROKE_DES_VELOCITY = 1.0f
        const val VELOCITY_FILTER_WEIGHT = 0.2f
    }

    private var canvasBmp: Canvas? = null
    private var ignoreTouch = false
    private var previousPoint: Point? = null
    private var startPoint: Point? = null
    private var currentPoint: Point? = null
    private var lastVelocity = 0f
    private var lastWidth = 0f
    private var paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintBm: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bmp: Bitmap? = null

    private var layoutLeft = 0
    private var layoutTop = 0
    private var layoutRight = 0
    private var layoutBottom = 0
    private var drawViewRect: Rect? = null
    private var penColor: Int
    private var backgroundColor: Int
    private var enableSignature: Boolean
    private var penSize: Float

    init {
        setWillNotDraw(false)
        isDrawingCacheEnabled = true

        val typedArray: TypedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.signature,
            0,
            0
        )

        backgroundColor = typedArray.getColor(
            R.styleable.signature_backgroundColor,
            resources.getColor(android.R.color.transparent)
        )
        penColor = typedArray.getColor(
            R.styleable.signature_penColor,
            resources.getColor(R.color.penRoyalBlue)
        )
        penSize = typedArray.getDimension(
            R.styleable.signature_penSize,
            resources.getDimension(R.dimen.pen_size)
        )
        enableSignature = typedArray.getBoolean(R.styleable.signature_enableSignature, true)
        typedArray.recycle()

        paint.apply {
            color = penColor
            isAntiAlias = true
            style = Paint.Style.FILL_AND_STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = penSize
        }

        paintBm.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = Color.BLACK
        }
    }

    /**************** Getter/Setter *****************/

    /** Get stroke size for signature creation */
    fun getPenSize(): Float {
        return penSize
    }

    /** Set stroke size for signature creation */
    fun setPenSize(penSize: Float) {
        this.penSize = penSize
    }

    /** Check if drawing on canvas is enabled or disabled */
    fun isEnableSignature(): Boolean {
        return enableSignature
    }

    /** Enable or disable drawing on canvas */
    fun setEnableSignature(enableSignature: Boolean) {
        this.enableSignature = enableSignature
    }

    /** Get stroke color for signature creation */
    fun getPenColor(): Int {
        return penColor
    }

    /** Set stroke color for signature creation */
    fun setPenColor(penColor: Int) {
        this.penColor = penColor
        paint.color = penColor
    }

    /** Get background color */
    fun getBackgroundColor(): Int {
        return backgroundColor
    }

    /** Set background color */
    override fun setBackgroundColor(backgroundColor: Int) {
        this.backgroundColor = backgroundColor
    }

    /** Clear signature from canvas */
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
        val newBottom = Math.max(bottom, bmp.height)
        val newRight = Math.max(right, bmp.width)
        newBitmapCanvas(left, top, newRight, newBottom)
        canvasBmp!!.drawBitmap(bmp, 0f, 0f, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnableSignature()) {
            return false
        }

        if (event.pointerCount > 1) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                ignoreTouch = false
                drawViewRect = Rect(left, top, right, bottom)
                onTouchDownEvent(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!drawViewRect!!.contains(left + event.x.toInt(), top + event.y.toInt())) {
                    // You are out of drawing area
                    if (!ignoreTouch) {
                        ignoreTouch = true
                        onTouchUpEvent(event.x, event.y)
                    }
                } else {
                    // You are in the drawing area
                    if (ignoreTouch) {
                        ignoreTouch = false
                        onTouchDownEvent(event.x, event.y)
                    } else {
                        onTouchMoveEvent(event.x, event.y)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onTouchUpEvent(event.x, event.y)
            }

            else -> {
            }
        }
        return true
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

        var velocity = currentPoint!!.velocityFrom(previousPoint!!)
        velocity = VELOCITY_FILTER_WEIGHT * velocity + (1 - VELOCITY_FILTER_WEIGHT) * lastVelocity

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
        bmp?.let { canvas.drawBitmap(it, 0f, 0f, paintBm) }
    }

    private fun drawLine(lastWidth: Float, currentWidth: Float, velocity: Float) {
        val mid1 = midPoint(previousPoint!!, startPoint!!)
        val mid2 = midPoint(currentPoint!!, previousPoint!!)
        draw(mid1, previousPoint!!, mid2, lastWidth, currentWidth, velocity)
    }

    private fun getPt(n1: Float, n2: Float, perc: Float): Float {
        val diff = n2 - n1
        return n1 + (perc * diff)
    }

    private fun midPoint(p1: Point, p2: Point): Point {
        return Point(getPt(p1.x, p2.x, 0.5f), getPt(p1.y, p2.y, 0.5f), 0)
    }

    private fun draw(
        start: Point,
        mid1: Point,
        mid2: Point,
        lastWidth: Float,
        currentWidth: Float,
        velocity: Float
    ) {
        if (canvasBmp != null) {
            val paint = Paint(paint)
            paint.strokeWidth = currentWidth
            canvasBmp!!.drawLine(start.x, start.y, mid1.x, mid1.y, paint)
            canvasBmp!!.drawLine(mid1.x, mid1.y, mid2.x, mid2.y, paint)
        }
    }

    fun getSignatureBitmap(): Bitmap? {
        return bmp
    }

    fun getSignatureBytes(): ByteArray? {
        val stream = ByteArrayOutputStream()
        bmp?.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()).apply {
            penColor = this@SignatureView.penColor
            penSize = this@SignatureView.penSize
            backgroundColor = this@SignatureView.backgroundColor
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            penColor = state.penColor
            penSize = state.penSize
            backgroundColor = state.backgroundColor
            paint.color = penColor
            paint.strokeWidth = penSize
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : BaseSavedState {
        var penColor: Int = Color.BLACK
        var penSize: Float = 0f
        var backgroundColor: Int = Color.WHITE

        constructor(superState: Parcelable?) : super(superState)

        constructor(parcel: Parcel) : super(parcel) {
            penColor = parcel.readInt()
            penSize = parcel.readFloat()
            backgroundColor = parcel.readInt()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeInt(penColor)
            parcel.writeFloat(penSize)
            parcel.writeInt(backgroundColor)
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
