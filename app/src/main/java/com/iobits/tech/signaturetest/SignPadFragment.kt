package com.iobits.tech.signaturetest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.iobits.tech.signaturetest.databinding.FragmentSignPadBinding

class SignPadFragment : Fragment() {
    private val binding by lazy {
        FragmentSignPadBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return binding.root
    }
}