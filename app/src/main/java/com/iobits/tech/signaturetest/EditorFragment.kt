package com.iobits.tech.signaturetest

import android.content.ContentValues.TAG
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.iobits.tech.pdfsign.document.PDSPageViewer
import com.iobits.tech.pdfsign.document.PdfDocumentProvider
import com.iobits.tech.pdfsign.imageviewer.PDSPageAdapter
import com.iobits.tech.pdfsign.pdf.PDSPDFDocument
import com.iobits.tech.pdfsign.pdf.PDSPDFPage
import com.iobits.tech.pdfsign.pds_model.PDSElement
import com.iobits.tech.signaturetest.databinding.FragmentEditorBinding
import com.iobits.tech.signaturetest.extensions.showToast
import com.iobits.tech.signaturetest.extensions.visible

class EditorFragment : Fragment(), PdfDocumentProvider {
    private val binding by lazy {
        FragmentEditorBinding.inflate(layoutInflater)
    }
    private var imageAdapter: PDSPageAdapter? = null
    private var document: PDSPDFDocument? = null
    private var mVisibleWindowHt: Int = 0
    private var pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { uris ->
                Log.d(TAG, uris.toString())
                binding.viewpager.visible().also {
                    openPDFViewer(uris)
                }
            } ?: kotlin.run {
                Log.d(TAG, "cant open file")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding.apply {
            parent.setOnClickListener {
                pickPdfLauncher.launch("application/pdf") // Set the MIME type to PDF files

            }
            addSign.setOnClickListener {
                val bitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val randomColor = (0xFF000000 or (Math.random() * 0xFFFFFF).toLong()).toInt()
                val paint = Paint().apply {
                    color = randomColor
                }
                canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
                addElement(
                    bitmap,
                    resources.getDimension(com.iobits.tech.pdfsign.R.dimen.sign_field_default_height),
                    resources.getDimension(com.iobits.tech.pdfsign.R.dimen.sign_field_default_height)
                )
            }
        }
        return binding.root
    }

    private fun openPDFViewer(pdfData: Uri?) {
        try {
            document = PDSPDFDocument(requireContext(), pdfData)
            document?.open()

            if (document == null) return

            imageAdapter = childFragmentManager?.let {
                PDSPageAdapter(
                    it, document!!, visibleWindowHeight
                )
            }
            binding.viewpager.adapter = imageAdapter
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                "Cannot open PDF, either PDF is corrupted or password protected",
                Toast.LENGTH_LONG
            ).show()
            Log.d("fileViewer", "openPDFViewer: $e")
//            finish()
        }
    }

    private fun computeVisibleWindowHtForNonFullScreenMode(): Int {
        return binding.parent.height
    }

    private val visibleWindowHeight: Int
        get() {
            if (mVisibleWindowHt == 0) {
                mVisibleWindowHt = computeVisibleWindowHtForNonFullScreenMode()
            }
            return mVisibleWindowHt
        }

    override fun getDocumentPage(pageNum: Int): PDSPDFPage? {
        return document?.getPage(pageNum)
    }

    private fun addElement(bitmap: Bitmap?, f: Float, f2: Float) {
        try {
            val focusedChild: View? = binding.viewpager.focusedChild
            if (focusedChild != null && bitmap != null) {
                val fASPageViewer: PDSPageViewer? =
                    (focusedChild as? ViewGroup)?.getChildAt(0) as? PDSPageViewer
                if (fASPageViewer != null) {
                    val visibleRect: RectF = fASPageViewer.visibleRect
                    val width: Float =
                        (visibleRect.left + (visibleRect.width() / 2.0f)) - (f / 2.0f)
                    val height: Float =
                        (visibleRect.top + (visibleRect.height() / 2.0f)) - (f2 / 2.0f)
                    fASPageViewer.lastFocusedElementViewer

                    fASPageViewer.createElement(
                        PDSElement.PDSElementType.PDSElementTypeImage, bitmap, width, height, f, f2
                    )
                } else {
                }
//            invokeMenuButton(true)
            } else {
                val focusedChild: View? = binding.viewpager.getChildAt(0)
                focusedChild?.requestFocus()

//            showToast("null")
                val fASPageViewer: PDSPageViewer? =
                    (focusedChild as? ViewGroup)?.getChildAt(0) as? PDSPageViewer
                if (fASPageViewer != null) {
                    val visibleRect: RectF = fASPageViewer.visibleRect
                    val width: Float =
                        (visibleRect.left + (visibleRect.width() / 2.0f)) - (f / 2.0f)
                    val height: Float =
                        (visibleRect.top + (visibleRect.height() / 2.0f)) - (f2 / 2.0f)
                    fASPageViewer.lastFocusedElementViewer

                    fASPageViewer.createElement(
                        PDSElement.PDSElementType.PDSElementTypeImage, bitmap, width, height, f, f2
                    )
                } else {
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "addElement: $e")
            showToast("Unable to add element")
        }
    }

}