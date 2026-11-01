package com.yagay.ListCleaner.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.yagay.ListCleaner.R

/**
 * A real editable TextView hosts Android's selection ActionMode.
 * This invokes the platform's PROCESS_TEXT discovery instead of simulating
 * third-party options. Long-press the sample text if the OEM suppresses the
 * automatic contextual toolbar.
 */
internal class TextSelectionProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val margin = (24 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        val info = TextView(this).apply {
            text = "Select the text below to open Android's text actions. Long-press if the toolbar is hidden."
            textSize = 16f
            gravity = Gravity.START
        }
        val editor = EditText(this).apply {
            setText(getString(R.string.system_preview_example_text))
            textSize = 18f
            setSingleLine(false)
            setSelectAllOnFocus(false)
        }
        layout.addView(info, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(editor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(layout)
        editor.post {
            editor.requestFocus()
            editor.selectAll()
            editor.performLongClick()
        }
    }
}
