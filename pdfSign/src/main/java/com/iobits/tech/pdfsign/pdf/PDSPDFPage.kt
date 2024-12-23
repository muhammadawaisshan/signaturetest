package com.iobits.tech.pdfsign.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.util.SizeF
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.setMargins
import com.iobits.tech.pdfsign.document.PDSPageViewer
import com.iobits.tech.pdfsign.pds_model.PDSElement


class PDSPDFPage(private val number: Int, val document: PDSPDFDocument) {
    private val elements: ArrayList<PDSElement> = arrayListOf()
    private var mPageSize: SizeF? = null
    var pageViewer: PDSPageViewer? = null


    val pageSize: SizeF?
        get() {
            if (mPageSize == null) {
                synchronized(PDSPDFDocument.lockObject) {
                    synchronized(document) {
                        val openPage = document.renderer?.openPage(
                            number
                        )
                        mPageSize = SizeF(openPage?.width!!.toFloat(), openPage.height.toFloat())
                        openPage.close()
                    }
                }
            }
            return mPageSize
        }

    fun renderPage(bitmap: Bitmap?) {
        synchronized(PDSPDFDocument.lockObject) {
            synchronized(document) {
                val openPage = document.renderer?.openPage(
                    number
                )
                mPageSize = SizeF(openPage?.width!!.toFloat(), openPage.height.toFloat())
                openPage.render(bitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                openPage.close()
            }
        }
    }

    // Method to handle double-click events

    fun removeElement(fASElement: PDSElement?) {
        elements.remove(fASElement)
    }

    fun addElement(fASElement: PDSElement) {
        elements.add(fASElement)
    }

    val numElements: Int
        get() = elements.size

    fun getElement(i: Int): PDSElement {
        return elements[i]
    }

    fun updateElement(
        fASElement: PDSElement,
        rectF: RectF?,
        f: Float,
        f2: Float,
        f3: Float,
        f4: Float
    ) {
        if (rectF != fASElement.rect) {
            fASElement.rect = rectF
        }
        if (!(f == 0.0f || f == fASElement.size)) {
            fASElement.size = f
        }
        if (!(f2 == 0.0f || f2 == fASElement.maxWidth)) {
            fASElement.maxWidth = f2
        }
        if (!(f3 == 0.0f || f3 == fASElement.strokeWidth)) {
            fASElement.strokeWidth = f3
        }
        if (!(f4 == 0.0f || f4 == fASElement.letterSpace)) {
            fASElement.letterSpace = f4
        }
    }

}