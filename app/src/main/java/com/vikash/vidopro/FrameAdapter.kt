package com.vikash.vidopro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class FrameAdapter(
    private var frameBitmaps: List<Bitmap>,
    var itemWidth: Int
) : RecyclerView.Adapter<FrameAdapter.FrameViewHolder>() {

    private var defaultPlaceholder: Bitmap? = null

    class FrameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.frameImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FrameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_frame, parent, false)
        return FrameViewHolder(view)
    }

    private fun getGrayPlaceholder(): Bitmap {
        val w = itemWidth.coerceAtLeast(60)
        val h = 160
        val current = defaultPlaceholder
        if (current != null && !current.isRecycled && current.width == w && current.height == h) {
            return current
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            color = Color.parseColor("#2E2E30")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Film strip border
        paint.color = Color.parseColor("#444446")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(1f, 1f, w - 1f, h - 1f, paint)

        defaultPlaceholder = bmp
        return bmp
    }

    override fun onBindViewHolder(holder: FrameViewHolder, position: Int) {
        holder.itemView.layoutParams = (holder.itemView.layoutParams ?: ViewGroup.LayoutParams(
            itemWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        )).apply {
            width = itemWidth
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }

        if (position < frameBitmaps.size && !frameBitmaps[position].isRecycled) {
            holder.imageView.setImageBitmap(frameBitmaps[position])
            holder.imageView.setBackgroundColor(Color.TRANSPARENT)
        } else {
            // Display default gray placeholder image instead of empty black screen
            holder.imageView.setImageBitmap(getGrayPlaceholder())
            holder.imageView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onViewRecycled(holder: FrameViewHolder) {
        super.onViewRecycled(holder)
        holder.imageView.setImageDrawable(null)
    }

    override fun getItemCount(): Int = 15

    fun updateFrames(newFrames: List<Bitmap>) {
        this.frameBitmaps = newFrames
        notifyDataSetChanged()
    }

    fun addFrame(bitmap: Bitmap) {
        val mut = this.frameBitmaps.toMutableList()
        mut.add(bitmap)
        this.frameBitmaps = mut
        notifyItemChanged(mut.size - 1)
    }
}
