package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.model.Item

class SlotAdapter : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    // 6칸 슬롯을 null로 초기화 (올바른 Item 타입 사용)
    private val slots = MutableList<Item?>(6) { null }

    class SlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val slotImage: ImageView = view.findViewById(R.id.itemImage)
        // itemName TextView는 item_slot.xml에 포함되어 있어야 함
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slot, parent, false)
        val layoutParams = view.layoutParams

        // 전체 너비를 6등분
        val totalWidth = parent.measuredWidth - (parent.paddingStart + parent.paddingEnd)
        val itemWidth = totalWidth / 6
        val itemHeight = (itemWidth * 1.5).toInt()

        layoutParams.width = itemWidth
        layoutParams.height = itemHeight
        view.layoutParams = layoutParams

        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val item = slots[position]

        if (item != null) {
            Glide.with(holder.itemView.context)
                .load(item.imageUrl)
                .override(holder.itemView.width, holder.itemView.width)
                .into(holder.slotImage)
            holder.itemView.setBackgroundResource(0)

            val itemName = holder.itemView.findViewById<TextView>(R.id.itemName)
            itemName.text = item.name
            itemName.visibility = View.VISIBLE

            holder.slotImage.setOnClickListener {
                removeItemFromSlot(position)
            }
        } else {
            holder.slotImage.setImageResource(R.drawable.nullitem_image)
            val itemName = holder.itemView.findViewById<TextView>(R.id.itemName)
            itemName.text = ""
            itemName.visibility = View.INVISIBLE
            holder.slotImage.setOnClickListener(null)
        }
    }

    override fun getItemCount() = slots.size

    fun addItemToSlot(item: Item) {
        for (i in slots.indices) {
            if (slots[i] == null) {
                slots[i] = item
                notifyItemChanged(i)
                return
            }
        }
    }

    private fun removeItemFromSlot(position: Int) {
        slots[position] = null
        notifyItemChanged(position)
    }

    // 현재 슬롯 상태 반환 (순서대로 6개의 슬롯)
    fun getSlotItems(): List<Item?> {
        return slots.toList()
    }

    // 불러온 슬롯 데이터를 적용하는 메소드
    fun setSlots(newSlots: List<Item?>) {
        for (i in newSlots.indices) {
            slots[i] = newSlots[i]
        }
        notifyDataSetChanged()
    }
}
