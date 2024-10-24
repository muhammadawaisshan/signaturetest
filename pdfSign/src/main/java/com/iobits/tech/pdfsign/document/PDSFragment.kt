package com.iobits.tech.pdfsign.document

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.iobits.tech.pdfsign.R
import com.iobits.tech.pdfsign.pdf.PDSPDFPage


class PDSFragment : Fragment() {
    private var mPageViewer: PDSPageViewer? = null

    override fun onCreateView(
        layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?
    ): View? {
        val inflate =
            layoutInflater.inflate(R.layout.com_bk_signer_fragment_layout, viewGroup, false)
        val linearLayout = inflate.findViewById<View>(R.id.fragment) as LinearLayout

        try {
            val parent = parentFragment
            if (parent is PdfDocumentProvider) {
                val pageNum = requireArguments().getInt("pageNum")
                val visibleHeight = requireArguments().getInt("visibleHeight")
                mPageViewer = PDSPageViewer(
                    parent.requireContext(),
                    visibleHeight,
                    parent.getDocumentPage(pageNum)  // Use the method from the interface
                )
                linearLayout.addView(mPageViewer)
            } else {
                throw ClassCastException("Parent fragment must implement PdfDocumentProvider")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return inflate
    }

    override fun onDestroyView() {
        mPageViewer?.cancelRendering()
        mPageViewer = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(i: Int, visibleHeight: Int): PDSFragment {
            val fragment = PDSFragment()
            val bundle = Bundle()
            bundle.putInt("pageNum", i)
            bundle.putInt("visibleHeight", visibleHeight)
            fragment.arguments = bundle
            return fragment
        }
    }
}

interface PdfDocumentProvider {
    fun getDocumentPage(pageNum: Int): PDSPDFPage?
}