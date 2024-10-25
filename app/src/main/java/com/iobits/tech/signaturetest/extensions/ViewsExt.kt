package com.iobits.tech.signaturetest.extensions

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.shakeView(fragment: Fragment? = null,stopAfterTime: Long? = 1000, ) {
    val shakeAnimator = ObjectAnimator.ofFloat(this, "translationX", -10f, 10f)
    shakeAnimator.duration = 2000
    shakeAnimator.interpolator = CycleInterpolator(5f)
    shakeAnimator.repeatCount = ObjectAnimator.INFINITE
    shakeAnimator.repeatMode = ObjectAnimator.REVERSE
    shakeAnimator.start()
    fragment?.lifecycleScope?.launch {
        delay(stopAfterTime ?: 1000)
        shakeAnimator.cancel()
    }
}

fun ByteArray.toBitmap(): Bitmap? {
    return BitmapFactory.decodeByteArray(this, 0, size)
}

fun View.gone() {
    visibility = View.GONE
}

fun Any.logd(message: String, tag: String? = "SignatureMakerApp") {
    Log.d(tag, "mCustomLog:$message ")
}

fun EditText.onDone(callback: () -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            callback.invoke()
            return@setOnEditorActionListener true
        }
        false
    }
}

/**
 * Applies system window insets to the specified view, adjusting the margins as specified.
 *
 * @param view The view to which the window insets should be applied.
 * @param applyTopMargin Whether to apply the top margin.
 * @param applyBottomMargin Whether to apply the bottom margin.
 * @param applyStartMargin Whether to apply the start margin.
 * @param applyEndMargin Whether to apply the end margin.
 */
fun View.applyInsets(
    applyTopMargin: Boolean = false,
    topExtraMarginDp: Int = 0,
    applyBottomMargin: Boolean = false,
    bottomExtraMarginDp: Int = 0,
    applyStartMargin: Boolean = false,
    startExtraMarginDp: Int = 0,
    applyEndMargin: Boolean = false,
    endExtraMarginDp: Int = 0
) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val density = Resources.getSystem().displayMetrics.density

        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (applyTopMargin) topMargin = insets.top + (topExtraMarginDp * density).toInt()
            if (applyBottomMargin) bottomMargin =
                insets.bottom + (bottomExtraMarginDp * density).toInt()
            if (applyStartMargin) marginStart = insets.left + (startExtraMarginDp * density).toInt()
            if (applyEndMargin) marginEnd = insets.right + (endExtraMarginDp * density).toInt()
        }
//use .Consumed if dont want others to take insets
        windowInsets
    }
}

fun Fragment.disableMultipleClicking(view: View, delay: Long = 750) {
    view.isEnabled = false
    this.lifecycleScope.launch {
        delay(delay)
        view.isEnabled = true
    }
}

// Extension function for TextView to set colored text spans
// Extension function for TextView to set colored text spans
fun TextView.setColoredText(text: String, vararg coloredWords: Pair<String, Int>) {
    val spannable = SpannableStringBuilder(text)

    coloredWords.forEach { (word, colorResId) ->
        val color = ContextCompat.getColor(context, colorResId)
        val startIndex = text.indexOf(word)
        val endIndex = startIndex + word.length
        if (startIndex != -1) { // Ensure the word exists in the text
            spannable.setSpan(
                ForegroundColorSpan(color), startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    setText(spannable, TextView.BufferType.SPANNABLE)
}

fun BottomSheetDialogFragment.openBottomSheet(activity: FragmentActivity?) {
    if (this.isAdded) {
        this.dismiss()
    }
    activity?.supportFragmentManager?.let { fragmentManager ->
        this.show(fragmentManager, this.tag)
    }
}
fun DialogFragment.openDialog(activity: FragmentActivity?) {
    if (this.isAdded) {
        this.dismiss()
    }
    activity?.supportFragmentManager?.let { fragmentManager ->
        this.show(fragmentManager, this.tag)
    }
}
fun BottomSheetDialogFragment.dismissSafely() {
    if (this.isAdded) {
        this.dismiss()
    }
}

fun Fragment.handleBackPress(onBackPressed: () -> Unit) {
    var lastBackPressedTime = 0L  // Variable to store the last back button press time

    requireView().isFocusableInTouchMode = true
    requireView().requestFocus()
    requireView().setOnKeyListener { _, keyCode, event ->
        if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressedTime > 1000) {  // Check if more than 2 seconds have passed
                lastBackPressedTime = currentTime
                onBackPressed() // Call the provided callback function
            }
            true
        } else false
    }
}

fun Resources.decodeSampledBitmapFromResource(
    resId: Int, reqWidth: Int, reqHeight: Int
): Bitmap {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeResource(this@decodeSampledBitmapFromResource, resId, this)
        inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
        inJustDecodeBounds = false
    }
    return BitmapFactory.decodeResource(this@decodeSampledBitmapFromResource, resId, options)
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

fun View.setSampledBitmapAsBackground(resources: Resources, resId: Int) {
    val scaledBitmap = resources.decodeSampledBitmapFromResource(resId, 100, 100)
    this.background = BitmapDrawable(resources, scaledBitmap)
}

fun Fragment.navigateTo(actionId: Int, destinationName: Int) {
    findNavController().navigate(
        actionId, null, NavOptions.Builder().setPopUpTo(destinationName, true).build()
    )
}

/**
 * for sending bundle along with navigation
 * */
fun Fragment.navigateSafe(
    actionId: Int, currentDestinationFragmentId: Int, bundle: Bundle? = null
) {
    if (findNavController().currentDestination?.id == currentDestinationFragmentId) {
        findNavController().navigate(
            actionId, bundle
        )
    } else {
        Log.d("TAG", "navigateSafe: ")
    }
}

fun Fragment.printLogs(msg: Any, tag: String? = null) {
    val fragmentName = tag ?: this.javaClass.simpleName
    Log.d(fragmentName, "$fragmentName: $msg")
}

fun Any.printLogs(msg: Any, tag: String? = null) {
    Log.d("SignatureMakerApp $tag", "SignatureMakerApp: $msg")
}

fun String.toEditable(): Editable = Editable.Factory.getInstance().newEditable(this)
fun Fragment.showToast(string: String) {
    Toast.makeText(this.requireContext(), string, Toast.LENGTH_SHORT).show()
}

fun Activity.showToast(string: String) {
    Toast.makeText(this, string, Toast.LENGTH_SHORT).show()
}

fun Fragment.showLongToast(string: String) {
    Toast.makeText(this.requireContext(), string, Toast.LENGTH_LONG).show()
}

fun Fragment.showKeyboard(view: View?) {
    view?.let {
        val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
    }
}

fun EditText.textWatcher(onTextChanged: (String?) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//            onTextChanged(s)
        }

        override fun afterTextChanged(s: Editable?) {
            s?.let {
                onTextChanged(s.toString())
            } ?: onTextChanged(null)

        }
    })
}

fun TextView.applyTextShader(
    colors: List<Int> = listOf(
        Color.parseColor("#3363F2"), Color.parseColor("#FF48E0")
    )
) {
    val width = paint?.measureText(text.toString())
    val textShader: Shader = LinearGradient(
        0f, 0f, width ?: 0f, textSize, colors.toIntArray(), null, Shader.TileMode.REPEAT
    )

    paint.setShader(textShader)
}

fun Fragment.hideKeyboard(view: View?): Boolean {
    val inputMethodManager =
        view?.context?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as? InputMethodManager
    return inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0) ?: false
}

fun Context.showEmailChooser(supportEmail: String, subject: String, body: String? = null) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        val chooser = Intent.createChooser(intent, "Send Email")
        if (chooser.resolveActivity(packageManager) != null) {
            startActivity(chooser)
        } else {
            Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show()
        }
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No email client found", Toast.LENGTH_SHORT).show()
    }
}

fun View.animateView() {
    val scaleX: ObjectAnimator = ObjectAnimator.ofFloat(this, "scaleX", 0.9f, 1.1f)
    val scaleY: ObjectAnimator = ObjectAnimator.ofFloat(this, "scaleY", 0.9f, 1.1f)
    scaleX.repeatCount = ObjectAnimator.INFINITE
    scaleX.repeatMode = ObjectAnimator.REVERSE
    scaleY.repeatCount = ObjectAnimator.INFINITE
    scaleY.repeatMode = ObjectAnimator.REVERSE
    val scaleAnim = AnimatorSet()
    scaleAnim.duration = 1000
    scaleAnim.play(scaleX).with(scaleY)
    scaleAnim.start()

}

fun View.animateMinorly() {
    val scaleX: ObjectAnimator = ObjectAnimator.ofFloat(this, "scaleX", 0.95f, 1.05f)
    val scaleY: ObjectAnimator = ObjectAnimator.ofFloat(this, "scaleY", 0.95f, 1.05f)
    scaleX.repeatCount = ObjectAnimator.INFINITE
    scaleX.repeatMode = ObjectAnimator.REVERSE
    scaleY.repeatCount = ObjectAnimator.INFINITE
    scaleY.repeatMode = ObjectAnimator.REVERSE
    val scaleAnim = AnimatorSet()
    scaleAnim.duration = 1000
    scaleAnim.play(scaleX).with(scaleY)
    scaleAnim.start()
}


