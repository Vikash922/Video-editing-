package com.vikash.vidopro.editor.dialogs

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vikash.vidopro.R
import com.vikash.vidopro.utils.FontItem
import com.vikash.vidopro.utils.FontManager
import com.vikash.vidopro.utils.setBounceClickListener

object FontSelectionDialog {

    fun show(
        context: Context,
        defaultFontFilePath: String?,
        currentFontPath: String?,
        onFontSelected: (FontItem) -> Unit,
        onImportFontRequested: () -> Unit,
        onFontsChanged: () -> Unit
    ): BottomSheetDialog {
        val bottomSheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_font_manager, null)
        bottomSheet.setContentView(view)

        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseFontManager)
        val btnImport = view.findViewById<Button>(R.id.btnImportFont)
        val rvFonts = view.findViewById<RecyclerView>(R.id.rvFonts)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyFonts)

        btnClose?.setBounceClickListener {
            bottomSheet.dismiss()
        }

        fun refreshFontList() {
            val allFonts = FontManager.getAllFonts(context, defaultFontFilePath)
            if (allFonts.isEmpty()) {
                tvEmpty?.visibility = View.VISIBLE
                rvFonts?.visibility = View.GONE
            } else {
                tvEmpty?.visibility = View.GONE
                rvFonts?.visibility = View.VISIBLE

                rvFonts?.layoutManager = LinearLayoutManager(context)
                rvFonts?.adapter = FontManagerAdapter(
                    fonts = allFonts,
                    selectedPath = currentFontPath,
                    onSelect = { fontItem ->
                        onFontSelected(fontItem)
                        bottomSheet.dismiss()
                    },
                    onDelete = { fontItem ->
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Delete Font")
                            .setMessage("Are you sure you want to delete '${fontItem.name}'?")
                            .setPositiveButton("Delete") { _, _ ->
                                if (FontManager.deleteCustomFont(context, fontItem.path)) {
                                    Toast.makeText(context, "Font deleted", Toast.LENGTH_SHORT).show()
                                    refreshFontList()
                                    onFontsChanged()
                                } else {
                                    Toast.makeText(context, "Failed to delete font", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
            }
        }

        btnImport?.setBounceClickListener {
            onImportFontRequested()
        }

        refreshFontList()
        bottomSheet.show()
        return bottomSheet
    }

    private class FontManagerAdapter(
        private val fonts: List<FontItem>,
        private val selectedPath: String?,
        private val onSelect: (FontItem) -> Unit,
        private val onDelete: (FontItem) -> Unit
    ) : RecyclerView.Adapter<FontManagerAdapter.FontViewHolder>() {

        class FontViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvFontName)
            val tvPreview: TextView = view.findViewById(R.id.tvFontPreview)
            val tvBadge: TextView = view.findViewById(R.id.tvFontBadge)
            val ivSelected: ImageView = view.findViewById(R.id.ivFontSelected)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteFont)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_font_manager, parent, false)
            return FontViewHolder(view)
        }

        override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
            val font = fonts[position]
            holder.tvName.text = font.name
            holder.tvPreview.text = "Aa The quick brown fox 123"
            if (font.typeface != null) {
                holder.tvPreview.typeface = font.typeface
            } else {
                holder.tvPreview.typeface = Typeface.DEFAULT
            }

            if (font.isCustom) {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "Custom"
                holder.btnDelete.visibility = View.VISIBLE
                holder.btnDelete.setOnClickListener { onDelete(font) }
            } else {
                holder.tvBadge.visibility = View.GONE
                holder.btnDelete.visibility = View.GONE
            }

            val isSelected = selectedPath != null && font.path == selectedPath
            holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                onSelect(font)
            }
        }

        override fun getItemCount() = fonts.size
    }
}
