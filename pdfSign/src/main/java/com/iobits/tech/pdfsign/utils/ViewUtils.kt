package com.iobits.tech.pdfsign.utils

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.widget.ImageView
import com.iobits.tech.pdfsign.Signature.SignatureView
import com.iobits.tech.pdfsign.pds_model.PDSElement
import com.iobits.tech.pdfsign.Signature.SignatureUtils


object ViewUtils {
    fun constrainRectXY(rectF: RectF, rectF2: RectF?) {
        if (rectF.left < rectF2!!.left) {
            rectF.left = rectF2.left
        } else if (rectF.right > rectF2.right) {
            rectF.left = rectF2.right - rectF.width()
        }
        if (rectF.top < rectF2.top) {
            rectF.top = rectF2.top
        } else if (rectF.bottom > rectF2.bottom) {
            rectF.top = rectF2.bottom - rectF.height()
        }
    }

    fun createSignatureView(
        context: Context?,
        fASElement: PDSElement?,
        matrix: Matrix?
    ): SignatureView? {
        val createFreeHandView: SignatureView?
        val rectF = RectF(fASElement?.rect)
        var strokeWidth = fASElement?.strokeWidth
        if (matrix != null) {
            matrix.mapRect(rectF)
            strokeWidth = matrix.mapRadius(strokeWidth!!)
        }
        createFreeHandView =
            SignatureUtils.createFreeHandView(rectF.height().toInt(), fASElement?.file, context)
        if (createFreeHandView != null) {
            createFreeHandView.x = rectF.left
            createFreeHandView.y = rectF.top
        }
        return createFreeHandView
    }

    fun createImageView(context: Context?, fASElement: PDSElement?, matrix: Matrix?): ImageView? {
        val createFreeHandView: ImageView?
        val rectF = RectF(fASElement?.rect)
        matrix?.mapRect(rectF)
        createFreeHandView =
            SignatureUtils.createImageView(rectF.height().toInt(), fASElement?.bitmap, context)
        if (createFreeHandView != null) {
            createFreeHandView.x = rectF.left
            createFreeHandView.y = rectF.top
        }
        return createFreeHandView
    }
}