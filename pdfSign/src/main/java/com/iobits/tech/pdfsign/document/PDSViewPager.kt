package com.iobits.tech.pdfsign.document

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import fr.castorflex.android.verticalviewpager.VerticalViewPager

open class PDSViewPager : VerticalViewPager {

    private var mDownReceieved = true
    private var mPageUpdateListener: PageUpdateListener? = null

    constructor(context: Context?) : super(context) {
        mPageUpdateListener = context as? PageUpdateListener
        init()
    }

    constructor(context: Context?, attributeSet: AttributeSet?) : super(context, attributeSet) {
        mPageUpdateListener = context as? PageUpdateListener
        init()
    }

    private fun init() {
        setOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrollStateChanged(i: Int) {}
            override fun onPageScrolled(i: Int, f: Float, i2: Int) {}
            override fun onPageSelected(i: Int) {
                val focusedChild = this@PDSViewPager.focusedChild
                if (focusedChild != null) {
                    val pDSPageViewer = (focusedChild as ViewGroup).getChildAt(0) as PDSPageViewer
                    pDSPageViewer?.resetScale()
                }
                mPageUpdateListener?.updatePageNumbers(i + 1)
                Log.d("TAG", "onPageSelected: ")
            }
        })
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
            mDownReceieved = true
        }
        if (motionEvent.pointerCount <= 1 && mDownReceieved) {
            return super.onInterceptTouchEvent(motionEvent)
        }
        mDownReceieved = false
        return false
    }
}
interface PageUpdateListener {
    fun updatePageNumbers(pageNumber: Int)
}