package com.iobits.tech.pdfsign.imageviewer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.iobits.tech.pdfsign.document.PDSFragment
import com.iobits.tech.pdfsign.pdf.PDSPDFDocument


class PDSPageAdapter(
    fragmentManager: FragmentManager,
    private val mDocument: PDSPDFDocument, private val visibleHeight: Int
) : FragmentStatePagerAdapter(
    fragmentManager,
) {
    override fun getCount(): Int {
        return mDocument.numPages
    }

    override fun getItem(i: Int): Fragment {
        return PDSFragment.newInstance(i,visibleHeight)
    }
}