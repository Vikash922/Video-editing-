package com.vikash.vidopro.editor.dialogs

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vikash.vidopro.R
import com.vikash.vidopro.customviews.HSVColorPickerView

object CustomColorPickerDialog {

    fun show(
        context: Context,
        initialHex: String,
        onColorPicked: (String) -> Unit
    ): BottomSheetDialog {
        val bottomSheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_color_picker, null)
        bottomSheet.setContentView(view)

        val preview = view.findViewById<View>(R.id.colorPickerPreview)
        val hsvPicker = view.findViewById<HSVColorPickerView>(R.id.hsvColorPicker)
        val btnCancel = view.findViewById<Button>(R.id.btnCancelCustomColor)
        val btnApply = view.findViewById<Button>(R.id.btnApplyCustomColor)

        var currentColor = try {
            Color.parseColor(initialHex)
        } catch (_: Exception) {
            Color.WHITE
        }
        preview.setBackgroundColor(currentColor)

        hsvPicker.setColor(currentColor)
        hsvPicker.onColorChanged = { newColor ->
            currentColor = newColor
            preview.setBackgroundColor(currentColor)
        }

        btnCancel.setOnClickListener { bottomSheet.dismiss() }
        btnApply.setOnClickListener {
            val hex = String.format("#%06X", 0xFFFFFF and currentColor)
            onColorPicked(hex)
            bottomSheet.dismiss()
        }
        bottomSheet.show()
        return bottomSheet
    }
}
