package com.iobits.tech.signaturetest

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.iobits.tech.signaturetest.databinding.FragmentSignPadBinding

class SignPadFragment(private val onDismissed: (Bitmap?) -> Unit) : DialogFragment() {
    private val binding by lazy {
        FragmentSignPadBinding.inflate(layoutInflater)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        setCancelable(false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.apply {
            doneBtn.setOnClickListener {
                val bitmap = signPad.getSignatureBitmap()
                onDismissed.invoke(bitmap)
                dismiss()
            }
            clearBtn.setOnClickListener {
                signPad.clearCanvas()
            }
        }

        return binding.root
    }
}