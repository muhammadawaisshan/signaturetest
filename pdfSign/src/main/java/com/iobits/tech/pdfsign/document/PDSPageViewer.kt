package com.iobits.tech.pdfsign.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.AsyncTask
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.util.SizeF
import android.view.DragEvent
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import com.iobits.tech.pdfsign.R
import com.iobits.tech.pdfsign.Signature.SignatureView
import com.iobits.tech.pdfsign.pdf.PDSPDFPage
import com.iobits.tech.pdfsign.pds_model.PDSElement
import com.iobits.tech.pdfsign.utils.PDSSignatureUtils
import com.iobits.tech.pdfsign.utils.ViewUtils
import java.util.Observable
import java.util.Observer


class PDSPageViewer(
    private val mContext: Context, val visibleWindowHeight: Int, pdfPage: PDSPDFPage?
) : FrameLayout(
    mContext
), Observer {
    private var isFirstTap: Boolean = true
    private val mImageView: ImageView
    private val mInflater: LayoutInflater
    private val mProgressView: LinearLayout
    private val mScaleGestureListener: ScaleGestureListener
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mGestureDetector: GestureDetector? = null
    private var mInitialRenderingTask: PDSRenderPageAsyncTask? = null
    private var mRenderPageTask: PDSRenderPageAsyncTask? = null
    var scaleFactor = 1.0f
        private set
    private var mScroll = PointF(0.0f, 0.0f)
    private var mFocus = PointF(0.0f, 0.0f)
    private var mStartScaleFactor = 1.0f
    private var maxScrollX = 0
    private var mMaxScrollY = 0
    var pageView: RelativeLayout? = null
    private var mScrollView: RelativeLayout? = null
    private var mScroller: OverScroller? = null
    private var mLastZoomTime: Long = 0
    private var mIsFirstScrollAfterIntercept = false
    private var mIsInterceptedScrolling = false
    private val mKeyboardHeight = 0
    private val mKeyboardShown = false
    var resizeInOperation = false
    private var mRenderPageTaskPending = false
    private var mBitmapScale = 1.0f
    val page: PDSPDFPage?
    var mInitialImageSize: SizeF? = null
    private var mImage: Bitmap? = null
    var imageContentRect: RectF? = null
        private set
    private var mToPDFCoordinatesMatrix: Matrix? = null
    private var mRenderingComplete = false
    var toViewCoordinatesMatrix: Matrix? = null
        private set
    private var mInterceptedDownX = 0.0f
    private var mInterceptedDownY = 0.0f
    private var mTouchSlop = 0.0f
    private var mLastDragPointX = -1.0f
    private var mLastDragPointY = -1.0f
    private var mElementPropMenu: View? = null
    var lastFocusedElementViewer: PDSElementViewer? = null
        private set
    private var mElementAlreadyPresentOnTap = false
    private var mElementCreationMenu: View? = null
    private var mTouchX = 0.0f
    private var mTouchY = 0.0f
    private var mDragShadowView: ImageView? = null

    init {
        page = pdfPage
        page?.pageViewer = this
        mInflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflate = mInflater.inflate(R.layout.com_bk_signer_pdfviewer, null, false)
        addView(inflate)
        mScrollView = inflate.findViewById(R.id.scrollview)
        pageView = inflate.findViewById(R.id.pageview)
        mImageView = inflate.findViewById(R.id.imageview)
        isHorizontalScrollBarEnabled = true
        isVerticalScrollBarEnabled = true
        isScrollbarFadingEnabled = true
        mProgressView = findViewById(R.id.linlaProgress)
        mProgressView.visibility = VISIBLE
        mScroller = OverScroller(context)
        mScaleGestureListener = ScaleGestureListener(this)
        mScaleGestureDetector = ScaleGestureDetector(mContext, mScaleGestureListener)
        mGestureDetector = GestureDetector(mContext, GestureListener(this))
        mGestureDetector?.setIsLongpressEnabled(true)
        requestFocus()
    }

    override fun update(o: Observable, arg: Any) {}
    private fun attachListeners() {
        setOnTouchListener { view, motionEvent ->
            val z =
                mGestureDetector!!.onTouchEvent(motionEvent) || mScaleGestureDetector!!.onTouchEvent(
                    motionEvent
                )
            if (!(z || motionEvent.action == 1)) {
                motionEvent.action
            }
            z
        }
        pageView!!.setOnDragListener { _, dragEvent ->
            when (dragEvent.action) {
                1 -> {
                    mLastDragPointX = -1.0f
                    mLastDragPointY = -1.0f
                }

                2 -> handleDragMove(dragEvent)
                3 -> {
                    mLastDragPointX = dragEvent.x
                    mLastDragPointY = dragEvent.y
                }

                4 -> handleDragEnd(dragEvent)
                5 -> {
                    mLastDragPointX = -1.0f
                    mLastDragPointY = -1.0f
                }
            }
            true
        }
    }

    private inner class GestureListener private constructor() :
        GestureDetector.SimpleOnGestureListener() {
        internal constructor(fASPageViewer: PDSPageViewer?) : this() {}

        override fun onDown(motionEvent: MotionEvent): Boolean {
            mScroller!!.forceFinished(true)
            ViewCompat.postInvalidateOnAnimation(this@PDSPageViewer)
            return true
        }


        override fun onFling(
            motionEvent: MotionEvent?, motionEvent2: MotionEvent, f: Float, f2: Float
        ): Boolean {
            if (motionEvent2.pointerCount > 1 || SystemClock.elapsedRealtime() - mLastZoomTime < 200L) {
                return false
            }
            mScroller!!.abortAnimation()
            mScroller?.fling(
                mScrollView!!.scrollX,
                mScrollView!!.scrollY,
                ((-f).toInt()) * 2,
                ((-f2).toInt()) * 2,
                0,
                maxScrollX,
                0,
                maxScrollY
            )
            ViewCompat.postInvalidateOnAnimation(this@PDSPageViewer)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val x = e.x
            val y = e.y
            onDoubleClick(
                x, y
            )                                                                       //the double tap coordinates
            return true
        }

        override fun onScroll(
            motionEvent: MotionEvent?, motionEvent2: MotionEvent, f: Float, f2: Float
        ): Boolean {

            if (mIsInterceptedScrolling && mIsFirstScrollAfterIntercept) {
                mIsFirstScrollAfterIntercept = false
                return false
            } else if (motionEvent2.pointerCount > 1 || SystemClock.elapsedRealtime() - mLastZoomTime < 200L) {
                return false
            } else {
                applyScroll(
                    mScrollView!!.scrollX + Math.round(f), mScrollView!!.scrollY + Math.round(f2)
                )
                return true
            }
        }

        override fun onLongPress(motionEvent: MotionEvent) {
            super.onLongPress(motionEvent)
            onTap(motionEvent, true)
        }

        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
            super.onSingleTapUp(motionEvent)
            mElementAlreadyPresentOnTap = false
            onTap(motionEvent, false)
            return true
        }
    }

    private inner class ScaleGestureListener private constructor() :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        internal constructor(fASPageViewer: PDSPageViewer?) : this() {}

        override fun onScaleBegin(scaleGestureDetector: ScaleGestureDetector): Boolean {
            return scaleBegin(scaleGestureDetector.focusX, scaleGestureDetector.focusY)
        }

        override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
            return scale(scaleGestureDetector.scaleFactor)
        }

        override fun onScaleEnd(scaleGestureDetector: ScaleGestureDetector) {
            scaleEnd()
        }

        fun resetScale() {
            mFocus = PointF(0.0f, 0.0f)
            mScroll = PointF(0.0f, 0.0f)
            mStartScaleFactor = 1.0f
            scaleFactor = 1.0f
            maxScrollX = 0
            mMaxScrollY = 0
            pageView!!.scaleX = scaleFactor
            pageView?.scaleY = scaleFactor
            mScrollView!!.scrollTo(0, 0)
            updateImageFoScale()
        }
    }

    fun resetScale() {
        mScaleGestureListener.resetScale()
    }

    private fun scaleBegin(f: Float, f2: Float): Boolean {
        pageView!!.pivotX = 0.0f
        pageView?.pivotY = 0.0f
        mFocus[f + (mScrollView!!.scrollX.toFloat())] = f2 + (mScrollView!!.scrollY.toFloat())
        mScroll[mScrollView!!.scrollX.toFloat()] = mScrollView!!.scrollY.toFloat()
        mStartScaleFactor = scaleFactor
        if (lastFocusedElementViewer != null) {
            if (mElementPropMenu != null) {
                mElementPropMenu!!.visibility = INVISIBLE
            } else if (mElementCreationMenu != null) {
                mElementCreationMenu!!.visibility = INVISIBLE
            }
            lastFocusedElementViewer!!.hideBorder()
        } else if (mElementCreationMenu != null) {
            mElementCreationMenu!!.visibility = INVISIBLE
        }
        return true
    }

    private fun scale(f: Float): Boolean {
        scaleFactor *= (f / 10000.0f) * 10000.0f
        scaleFactor = (scaleFactor / 10000.0f) * 10000.0f
        scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.0f))
        maxScrollX = Math.round((mScrollView!!.width.toFloat()) * (scaleFactor - 1.0f))
        mMaxScrollY = Math.round((mScrollView!!.height.toFloat()) * (scaleFactor - 1.0f))
        var round = Math.round((mFocus.x * ((scaleFactor / mStartScaleFactor) - 1.0f)) + mScroll.x)
        var round2 = Math.round((mFocus.y * ((scaleFactor / mStartScaleFactor) - 1.0f)) + mScroll.y)
        round = Math.max(0, Math.min(round, maxScrollX))
        round2 = Math.max(0, Math.min(round2, maxScrollY))
        pageView!!.scaleX = scaleFactor
        pageView?.scaleY = scaleFactor
        mScrollView?.scrollTo(round, round2)
        invalidate()
        return true
    }

    private fun scaleEnd() {
        mLastZoomTime = SystemClock.elapsedRealtime()
        updateImageFoScale()
        if (lastFocusedElementViewer == null) {
            return
        }
        if (mElementPropMenu != null) {
            showElementPropMenu(lastFocusedElementViewer)
        } else if (mElementCreationMenu != null) {
            //  showElementCreationMenu(this.mLastFocusedElementViewer);
        }
    }

    private fun applyScroll(i: Int, i2: Int) {
        mScrollView!!.scrollTo(
            Math.max(0, Math.min(i, maxScrollX)), Math.max(0, Math.min(i2, maxScrollY))
        )
    }

    private val maxScrollY: Int
        private get() {
            val i = mMaxScrollY
            return if (mKeyboardShown) i + mKeyboardHeight else i
        }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        val actionMasked = MotionEventCompat.getActionMasked(motionEvent)
        if (!resizeInOperation || scaleFactor == 1.0f) {
            return false
        }
        if (lastFocusedElementViewer != null) {
            val rect = Rect()
            lastFocusedElementViewer!!.containerView?.getHitRect(rect)
            if (rect.contains(
                    ((motionEvent.x + (mScrollView!!.scrollX.toFloat())) / scaleFactor).toInt(),
                    ((motionEvent.y + (mScrollView!!.scrollY.toFloat())) / scaleFactor).toInt()
                )
            ) {
                return false
            }
        }
        var z = true
        when (actionMasked) {
            1, 3 -> mIsInterceptedScrolling = false
            2 -> if (!mIsInterceptedScrolling) {
                val abs = Math.abs(motionEvent.x - mInterceptedDownX)
                val abs2 = Math.abs(motionEvent.y - mInterceptedDownY)
                if (abs > mTouchSlop || abs2 > mTouchSlop) {
                    mIsInterceptedScrolling = true
                    mIsFirstScrollAfterIntercept = true
                }
            }

            0 -> {
                mIsInterceptedScrolling = false
                mInterceptedDownX = motionEvent.x
                mInterceptedDownY = motionEvent.y
                mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
            }
        }
        z = false
        return z
    }

    private fun onTap(motionEvent: MotionEvent, z: Boolean) {
        manualScale(motionEvent.x, motionEvent.y)
        //getDocumentViewer().hideTrainingViewIfVisible();
        val x = (motionEvent.x + (mScrollView!!.scrollX.toFloat())) / scaleFactor
        val y = (motionEvent.y + (mScrollView!!.scrollY.toFloat())) / scaleFactor
        if (!imageContentRect!!.contains(
                RectF(
                    x,
                    y,
                    (resources.getDimension(R.dimen.element_min_width) + x) + (resources.getDimension(
                        R.dimen.element_horizontal_padding
                    ) * 2.0f),
                    (resources.getDimension(R.dimen.element_min_width) + y) + (resources.getDimension(
                        R.dimen.element_vertical_padding
                    ) * 2.0f)
                )
            )
        ) {
            if (PDSSignatureUtils.isSignatureMenuOpen) {
                PDSSignatureUtils.dismissSignatureMenu()
            }
            removeFocus()
        } else {
            mTouchX = x
            mTouchY = y
            if (PDSSignatureUtils.isSignatureMenuOpen) {
                PDSSignatureUtils.dismissSignatureMenu()
            } else if (z) {
                onLongTap(x, y)
            } else {
                onSingleTap(x, y)
            }
        }
    }

    private fun onLongTap(f: Float, f2: Float) {
        if (lastFocusedElementViewer != null) {
            removeFocus()
        }
    }

    private fun onSingleTap(f: Float, f2: Float) {
        if (lastFocusedElementViewer != null) {
            removeFocus()
            hideElementCreationMenu()
        }
    }

    private fun manualScale(f: Float, f2: Float) {
        if (scaleFactor == 1.0f && isFirstTap) {
            isFirstTap = false
            scaleBegin(f, f2)
            scale(1.15f)
            scale(1.3f)
            scale(1.45f)
            scaleEnd()
        }
    }

    private fun setImageBitmap(bitmap: Bitmap) {
        if (mImage != null) {
            mImage!!.recycle()
        }
        mImage = bitmap
        mImageView.setImageBitmap(bitmap)
    }

    public override fun onLayout(z: Boolean, i: Int, i2: Int, i3: Int, i4: Int) {
        super.onLayout(z, i, i2, i3, i4)
        if (z && mImageView.width > 0) {
            initRenderingAsync()
        }
    }

    public override fun onSizeChanged(i: Int, i2: Int, i3: Int, i4: Int) {
        if (mImageView.width > 0) {
            initRenderingAsync()
        }

    }

    /*fun onDoubleClick(x: Float, y: Float) {
        val bitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val randomColor = (0xFF000000 or (Math.random() * 0xFFFFFF).toLong()).toInt()
        val paint = Paint().apply {
            color = randomColor
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        createElement(
            PDSElement.PDSElementType.PDSElementTypeImage,
            bitmap,
            x,
            y,
            width,
            height
        )
    }*/
    fun onDoubleClick(x: Float, y: Float) {
        // Create the EditText dynamically
        val editText = EditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            hint = "Enter text"
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT
        }

        // Add the EditText to the page view
        pageView?.addView(editText)

        // Position the EditText at the clicked coordinates
        editText.x = x
        editText.y = y

        // Handle the done action
        editText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {

                // Get the input text
                val inputText = v.text.toString()

                // Check if the input is empty
                if (inputText.isNotBlank()) {
                    // Create bitmap from the input text
                    val bitmap = createBitmapFromText(inputText)

                    // Call createElement with the new bitmap
                    createElement(
                        PDSElement.PDSElementType.PDSElementTypeImage,
                        bitmap,
                        x,
                        y,
                        bitmap.width.toFloat(),
                        bitmap.height.toFloat()
                    )
                }

                // Remove the EditText from the parent regardless of input
                pageView?.removeView(editText).also {
                    pageView?.hideKeyboard()
                }

                true
            } else {
                false
            }
        }

        // Show the keyboard for user input
        editText.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    // Function to create a bitmap from the input text
    private fun createBitmapFromText(text: String): Bitmap {
        val paint = Paint().apply {
            color = Color.BLACK // Text color
            textSize = 50f // Set desired text size
            textAlign = Paint.Align.LEFT
        }

        // Measure text dimensions
        val textWidth = paint.measureText(text).toInt()
        val textHeight = (paint.descent() - paint.ascent()).toInt()

        // Create bitmap
        val bitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, -paint.ascent(), paint) // Draw the text onto the canvas
        return bitmap
    }


    private fun initRenderingAsync() {

        if ((mImage == null) && (mImageView.width > 0) && (mInitialRenderingTask == null || mInitialRenderingTask!!.status == AsyncTask.Status.FINISHED)) {
            mInitialRenderingTask = PDSRenderPageAsyncTask(page,
                SizeF(mImageView.width.toFloat(), mImageView.height.toFloat()),
                1.0f,
                object : PDSRenderPageAsyncTask.OnPostExecuteListener {
                    override fun onPostExecute(
                        fASRenderPageAsyncTask: PDSRenderPageAsyncTask, bitmap: Bitmap
                    ) {
                        if ((scaleFactor == 1.0f) && (fASRenderPageAsyncTask.page === page)) {
                            val visibleWindowHeight = visibleWindowHeight
                            if (visibleWindowHeight > 0) {
                                mScrollView!!.layoutParams = LayoutParams(-1, visibleWindowHeight)
                            }
                            mInitialImageSize = fASRenderPageAsyncTask.bitmapSize
                            computeImageContentRect()
                            computeCoordinateConversionMatrices()
                            setImageBitmap(bitmap)
                            renderElements()
                            mProgressView.visibility = INVISIBLE
                            attachListeners()
                        } else bitmap.recycle()
                        mRenderingComplete = true
                    }
                })
            mInitialRenderingTask!!.execute(*arrayOfNulls(0))
        }
    }

    private fun renderElements() {
        for (i in 0 until page!!.numElements) {
            addElement(page.getElement(i))
        }
    }

    private fun computeImageContentRect() {
        var width: Float
        val f: Float
        var width2 = mInitialImageSize!!.width / mInitialImageSize!!.height
        var f2 = 0.0f
        if (width2 >= (mImageView.width.toFloat()) / (mImageView.height.toFloat())) {
            width = mImageView.width.toFloat()
            width2 = width / width2
            f2 = ((mImageView.height.toFloat()) - width2) / 2.0f
            f = 0.0f
            val f3 = width
            width = width2
            width2 = f3
        } else {
            width = mImageView.height.toFloat()
            width2 *= width
            f = ((mImageView.width.toFloat()) - width2) / 2.0f
        }
        imageContentRect = RectF(f, f2, width2 + f, width + f2)
    }

    private fun computeCoordinateConversionMatrices() {
        val pageSize = page?.pageSize
        val imageContentRect = imageContentRect
        mToPDFCoordinatesMatrix = Matrix()
        mToPDFCoordinatesMatrix!!.postTranslate(
            0.0f - imageContentRect!!.left, 0.0f - imageContentRect.top
        )
        mToPDFCoordinatesMatrix!!.postScale(
            pageSize!!.width / imageContentRect.width(), pageSize.height / imageContentRect.height()
        )
        toViewCoordinatesMatrix = Matrix()
        toViewCoordinatesMatrix!!.postScale(
            imageContentRect.width() / pageSize.width, imageContentRect.height() / pageSize.height
        )
        toViewCoordinatesMatrix!!.postTranslate(imageContentRect.left, imageContentRect.top)
    }

    @Synchronized
    private fun updateImageFoScale() {
        if (scaleFactor != mBitmapScale) {
            if (mRenderPageTask != null) {
                mRenderPageTask!!.cancel(false)
                if (mRenderPageTask!!.status == AsyncTask.Status.RUNNING) {
                    mRenderPageTaskPending = true
                    return
                }
            }
            mRenderPageTask = PDSRenderPageAsyncTask(page,
                SizeF(mImageView.width.toFloat(), mImageView.height.toFloat()),
                scaleFactor,
                object : PDSRenderPageAsyncTask.OnPostExecuteListener {
                    override fun onPostExecute(
                        fASRenderPageAsyncTask: PDSRenderPageAsyncTask, bitmap: Bitmap
                    ) {
                        if (scaleFactor == fASRenderPageAsyncTask.scale) {
                            setImageBitmap(bitmap)
                            mBitmapScale = scaleFactor
                        } else bitmap.recycle()
                        if (mRenderPageTaskPending) {
                            mRenderPageTask = null
                            mRenderPageTaskPending = false
                            updateImageFoScale()
                        }
                    }
                })
            mRenderPageTask!!.execute(*arrayOfNulls(0))
        }
    }

    val visibleRect: RectF
        get() = RectF(
            (mScrollView!!.scrollX.toFloat()) / scaleFactor,
            (mScrollView!!.scrollY.toFloat()) / scaleFactor,
            ((mScrollView!!.scrollX + pageView!!.width).toFloat()) / scaleFactor,
            ((mScrollView!!.scrollY + pageView!!.height).toFloat()) / scaleFactor
        )

    fun cancelRendering() {
        if (mInitialRenderingTask != null) {
            mInitialRenderingTask!!.cancel(false)
        }
    }

    override fun computeScroll() {
        if (!mScroller!!.isFinished) {
            mScroller!!.computeScrollOffset()
            applyScroll(mScroller!!.currX, mScroller!!.currY)
        }
    }

    public override fun computeHorizontalScrollRange(): Int {
        return Math.round((mScrollView!!.width.toFloat()) * scaleFactor) - 1
    }

    public override fun computeVerticalScrollRange(): Int {
        return Math.round((mScrollView!!.height.toFloat()) * scaleFactor) - 1
    }

    public override fun computeHorizontalScrollOffset(): Int {
        return mScrollView!!.scrollX
    }

    public override fun computeVerticalScrollOffset(): Int {
        return mScrollView!!.scrollY
    }

    fun hideElementPropMenu() {
        if (mElementPropMenu != null) {
            mScrollView!!.removeView(mElementPropMenu)
            mElementPropMenu = null
        }
        if (lastFocusedElementViewer != null) {
            lastFocusedElementViewer!!.hideBorder()
            lastFocusedElementViewer = null
        }
    }

    /*fun createElement(
        fASElementType: PDSElement.PDSElementType,
        file: File?,
        f: Float,
        f2: Float,
        f3: Float,
        f4: Float
    ): PDSElement {
        val fASElement = PDSElement(fASElementType, file)
        fASElement.rect = mapRectToPDFCoordinates(RectF(f, f2, f + f3, f2 + f4))
        val addElement = addElement(fASElement)
        if (fASElementType == PDSElement.PDSElementType.PDSElementTypeSignature) {
            addElement.elementView?.requestFocus()
        }
        return fASElement
    }*/

    fun createElement(
        fASElementType: PDSElement.PDSElementType,
        bitmap: Bitmap?,
        f: Float,
        f2: Float,
        f3: Float,
        f4: Float
    ): PDSElement {
        val fASElement = PDSElement(fASElementType, bitmap)
        fASElement.rect = mapRectToPDFCoordinates(RectF(f, f2, f + f3, f2 + f4))
        val addElement = addElement(fASElement)
        addElement.elementView?.requestFocus()
        return fASElement
    }

    private fun addElement(fASElement: PDSElement?): PDSElementViewer {
        return PDSElementViewer(mContext, this, fASElement)
    }

    private fun mapRectToPDFCoordinates(rectF: RectF): RectF {
        mToPDFCoordinatesMatrix!!.mapRect(rectF)
        return rectF
    }

    fun mapLengthToPDFCoordinates(f: Float): Float {
        return mToPDFCoordinatesMatrix!!.mapRadius(f)
    }

    fun setElementAlreadyPresentOnTap(z: Boolean) {
        mElementAlreadyPresentOnTap = z
    }

    fun showElementPropMenu(fASElementViewer: PDSElementViewer?) {
        hideElementPropMenu()
        hideElementCreationMenu()
        fASElementViewer!!.showBorder()
        lastFocusedElementViewer = fASElementViewer
        val inflate =
            mInflater.inflate(R.layout.com_bk_signer_element_prop_menu_layout, null, false)
        inflate.tag = fASElementViewer
        mScrollView!!.addView(inflate)
        mElementPropMenu = inflate
        (inflate.findViewById<View>(R.id.delButton) as ImageButton).setOnClickListener {
            fASElementViewer.removeElement()
//                activity!!.invokeMenuButton(false)
        }
        setMenuPosition(fASElementViewer.containerView, inflate)
    }

    private fun hideElementCreationMenu() {
        if (mElementCreationMenu != null) {
            mScrollView!!.removeView(mElementCreationMenu)
            mElementCreationMenu = null
        }
    }

    private fun setMenuPosition(f: Float, f2: Float, view: View?, z: Boolean) {
        view!!.measure(0, 0)
        var f3 = scaleFactor * f
        if (z) {
            f3 -= (view.measuredWidth / 2).toFloat()
        }
        var dimension =
            (scaleFactor * f2) - ((resources.getDimension(R.dimen.menu_offset_y).toInt()).toFloat())
        val rectF = RectF(
            f3,
            dimension,
            (view.measuredWidth.toFloat()) + f3,
            (view.measuredHeight.toFloat()) + dimension
        )
        val visibleRect = visibleRect
        visibleRect.intersect((imageContentRect)!!)
        if (!visibleRect.contains(rectF)) {
            if (f3 > (visibleRect.right * scaleFactor) - (view.measuredWidth.toFloat())) {
                f3 = (visibleRect.right * scaleFactor) - (view.measuredWidth.toFloat())
            } else if (f3 < visibleRect.left * scaleFactor && z) {
                f3 = visibleRect.left * scaleFactor
            }
            if (dimension < visibleRect.top * scaleFactor) {
                if (z) {
                    dimension = (f2 * scaleFactor) + ((resources.getDimension(R.dimen.menu_offset_x)
                        .toInt()).toFloat())
                } else if (f3 > ((visibleRect.left * scaleFactor) + (view.measuredWidth.toFloat())) + ((resources.getDimension(
                        R.dimen.menu_offset_x
                    ).toInt()).toFloat())
                ) {
                    f3 =
                        ((f * scaleFactor) - (view.measuredWidth.toFloat())) - ((resources.getDimension(
                            R.dimen.menu_offset_x
                        ).toInt()).toFloat())
                    dimension = f2 * scaleFactor
                }
            }
        }
        view.x = f3
        view.y = dimension
    }

    private fun setMenuPosition(view: View?, view2: View?) {
        setMenuPosition(view!!.x, view.y, view2, false)
    }

    fun mapLengthToViewCoordinates(f: Float): Float {
        return toViewCoordinatesMatrix!!.mapRadius(f)
    }

    fun modifyElementSignatureSize(
        fASElement: PDSElement, view: View?, relativeLayout: RelativeLayout?, i: Int, i2: Int
    ) {
        val relativeLayout2 = relativeLayout
        val i3 = i
        val i4 = i2
        val f = i4.toFloat()
        if (imageContentRect!!.contains(
                RectF(
                    relativeLayout!!.x,
                    relativeLayout.y - f,
                    (relativeLayout.x + (relativeLayout.width.toFloat())) + (i3.toFloat()),
                    relativeLayout.y + (relativeLayout.height.toFloat())
                )
            )
        ) {
            if (view is SignatureView) {
                val signatureView = view
                signatureView.setLayoutParams(view.getWidth() + i3, view.getHeight() + i4)
                relativeLayout2!!.layoutParams = RelativeLayout.LayoutParams(
                    relativeLayout.width + i3, relativeLayout.height + i4
                )
                relativeLayout2.y = relativeLayout.y - f
                val fASElement2 = fASElement
                page!!.updateElement(
                    fASElement2, mapRectToPDFCoordinates(
                        RectF(
                            (relativeLayout.x.toInt()).toFloat(),
                            (relativeLayout.y.toInt()).toFloat(),
                            ((relativeLayout.x + (view.getWidth().toFloat())).toInt()).toFloat(),
                            ((relativeLayout.y + (view.getHeight().toFloat())).toInt()).toFloat()
                        )
                    ), 0.0f, 0.0f, signatureView.strokeWidth, 0.0f
                )
                setMenuPosition(relativeLayout2, mElementPropMenu)
            } else if (view is ImageView) {
                view.layoutParams =
                    RelativeLayout.LayoutParams(view.getWidth() + i3, view.getHeight() + i4)
                relativeLayout2!!.layoutParams = RelativeLayout.LayoutParams(
                    relativeLayout.width + i3, relativeLayout.height + i4
                )
                relativeLayout2.y = relativeLayout.y - f
                val fASElement2 = fASElement
                page!!.updateElement(
                    fASElement2, mapRectToPDFCoordinates(
                        RectF(
                            (relativeLayout.x.toInt()).toFloat(),
                            (relativeLayout.y.toInt()).toFloat(),
                            ((relativeLayout.x + (view.getWidth().toFloat())).toInt()).toFloat(),
                            ((relativeLayout.y + (view.getHeight().toFloat())).toInt()).toFloat()
                        )
                    ), 0.0f, 0.0f, 0.0f, 0.0f
                )
                setMenuPosition(relativeLayout2, mElementPropMenu)
            }
        }
    }

    fun removeFocus() {
        if (Build.VERSION.SDK_INT < 28) {
            clearFocus()
        }
        hideElementPropMenu()
        hideElementCreationMenu()
        if (Build.VERSION.SDK_INT >= 28) {
            mImageView.requestFocus()
        }
    }

    private fun handleDragMove(dragEvent: DragEvent) {
        val dragEventData = dragEvent.localState as PDSElementViewer.DragEventData
        val fASElementViewer = dragEventData.viewer
        val elementView = fASElementViewer.elementView
        mLastDragPointX = dragEvent.x
        mLastDragPointY = dragEvent.y
        if (mDragShadowView == null) {
            hideDragElement(fASElementViewer)
            fASElementViewer.element
            initDragShadow(elementView)
        }
        val rectF = RectF(
            mLastDragPointX - dragEventData.x,
            mLastDragPointY - dragEventData.y,
            ((elementView!!.width.toFloat()) + mLastDragPointX) - dragEventData.x,
            ((elementView!!.height.toFloat()) + mLastDragPointY) - dragEventData.y
        )
        ViewUtils.constrainRectXY(rectF, imageContentRect)
        mLastDragPointX = rectF.left + dragEventData.x
        mLastDragPointY = rectF.top + dragEventData.y
        updateDragShadow(mLastDragPointX, mLastDragPointY, dragEventData.x, dragEventData.y)
    }

    private fun updateDragShadow(f: Float, f2: Float, f3: Float, f4: Float) {
        if (mDragShadowView != null) {
            mDragShadowView!!.x = f - f3
            mDragShadowView!!.y = f2 - f4
        }
    }

    private fun handleDragEnd(dragEvent: DragEvent) {
        if (mLastDragPointX != -1.0f || mLastDragPointY != -1.0f) {
            val f: Float
            val dragEventData = dragEvent.localState as PDSElementViewer.DragEventData
            val fASElementViewer = dragEventData.viewer
            val elementView = fASElementViewer.elementView
            var f2 = mLastDragPointX - dragEventData.x
            var f3 = mLastDragPointY - dragEventData.y
            var width = elementView!!.width
            var height = elementView!!.height
            width = fASElementViewer.containerView!!.width
            height = fASElementViewer.containerView!!.height
            val f4 = width.toFloat()
            val f5 = height.toFloat()
            val rectF = RectF(f2, f3, f2 + f4, f3 + f5)
            var imageContentRect = imageContentRect
            if (!imageContentRect!!.contains(rectF)) {
                if (f2 < imageContentRect.left) {
                    f2 = imageContentRect.left
                } else if (f2 > imageContentRect.right - f4) {
                    f2 = imageContentRect.right - f4
                }
                if (f3 < imageContentRect.top) {
                    f3 = imageContentRect.top
                } else if (f3 > imageContentRect.bottom - f5) {
                    f3 = imageContentRect.bottom - f5
                }
            }
            val containerView = fASElementViewer.containerView
            containerView!!.x = f2
            containerView.y = f3
            containerView.visibility = VISIBLE
            elementView.visibility = VISIBLE
            f = 0.0f
            imageContentRect = RectF(
                (f2.toInt()).toFloat(),
                (f3.toInt()).toFloat(),
                ((f2 + (elementView.width.toFloat())).toInt()).toFloat(),
                ((f3 + (elementView.height.toFloat())).toInt()).toFloat()
            )
            mapRectToPDFCoordinates(imageContentRect)
            page!!.updateElement(
                elementView.tag as PDSElement, imageContentRect, 0.0f, f, 0.0f, 0.0f
            )
            showElementPropMenu(fASElementViewer)
            releaseDragShadow()
        }
    }

    private fun hideDragElement(fASElementViewer: PDSElementViewer?) {
        removeFocus()
        fASElementViewer!!.showBorder()
        fASElementViewer.containerView?.visibility = INVISIBLE
        fASElementViewer.elementView?.visibility = INVISIBLE
    }

    private fun initDragShadow(view: View?) {
        if (mDragShadowView == null) {
            val createBitmap =
                Bitmap.createBitmap(view!!.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(createBitmap))
            mDragShadowView = ImageView(mContext)
            mDragShadowView!!.setImageBitmap(createBitmap)
            mDragShadowView!!.imageAlpha = DRAG_SHADOW_OPACITY
            mDragShadowView!!.layoutParams = RelativeLayout.LayoutParams(view.width, view.height)
            pageView!!.addView(mDragShadowView)
        }
    }

    private fun releaseDragShadow() {
        if (mDragShadowView != null) {
            mDragShadowView!!.visibility = INVISIBLE
            pageView!!.removeView(mDragShadowView)
            mDragShadowView = null
        }
    }

    companion object {
        private val DRAG_SHADOW_OPACITY = 180
    }
}
fun View.hideKeyboard(): Boolean {
    val inputMethodManager =
        this?.context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as? InputMethodManager
    return inputMethodManager?.hideSoftInputFromWindow(this.windowToken, 0) ?: false
}