package com.vikash.vidopro.editor.dialogs

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.slider.Slider
import com.vikash.vidopro.R
import com.vikash.vidopro.customviews.HSVColorPickerView
import com.vikash.vidopro.utils.setBounceClickListener

object ChromaKeyDialog {

    fun show(
        activity: Activity,
        initialColor: String?,
        initialSimilarity: Float,
        onPreview: (color: String, similarity: Float) -> Unit,
        onApply: (color: String?, similarity: Float) -> Unit,
        onClear: () -> Unit,
        onRequestEyedropper: (onColorPicked: (String) -> Unit) -> Unit,
        onDismissWithoutApply: () -> Unit
    ): BottomSheetDialog {
        val bottomSheet = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.chroma_key_bottom_sheet_dialog, null)
        bottomSheet.setContentView(view)

        val colorPreview = view.findViewById<View>(R.id.chromaColorPreview)
        val hsvPicker = view.findViewById<HSVColorPickerView>(R.id.hsvChromaColorPicker)
        val slider = view.findViewById<Slider>(R.id.chromaIntensitySlider)
        val btnClear = view.findViewById<Button>(R.id.btnChromaClear)
        val btnApply = view.findViewById<Button>(R.id.btnChromaApply)
        val btnEyedropper = view.findViewById<ImageView>(R.id.btnChromaEyedropper)

        var selectedColor = initialColor ?: "#00FF00"
        slider.value = initialSimilarity.coerceIn(0.01f, 0.5f)

        fun updatePreview() {
            try {
                colorPreview.setBackgroundColor(Color.parseColor(selectedColor))
                onPreview(selectedColor, slider.value)
            } catch (_: Exception) {}
        }

        try {
            colorPreview.setBackgroundColor(Color.parseColor(selectedColor))
            hsvPicker.setColor(Color.parseColor(selectedColor))
        } catch (_: Exception) {
            hsvPicker.setColor(Color.GREEN)
        }

        hsvPicker.onColorChanged = { newColor ->
            selectedColor = String.format("#%06X", (0xFFFFFF and newColor))
            updatePreview()
        }

        slider.addOnChangeListener { _, _, _ -> updatePreview() }

        btnEyedropper.setBounceClickListener {
            bottomSheet.hide()
            onRequestEyedropper { hex ->
                selectedColor = hex
                try {
                    hsvPicker.setColor(Color.parseColor(selectedColor))
                } catch (_: Exception) {}
                updatePreview()
                bottomSheet.show()
            }
        }

        var applied = false

        btnClear.setOnClickListener {
            applied = true
            onClear()
            bottomSheet.dismiss()
        }

        btnApply.setOnClickListener {
            applied = true
            onApply(selectedColor, slider.value)
            bottomSheet.dismiss()
        }

        bottomSheet.setOnDismissListener {
            if (!applied) {
                onDismissWithoutApply()
            }
        }

        bottomSheet.show()
        return bottomSheet
    }
}
