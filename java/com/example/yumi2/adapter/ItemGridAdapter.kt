package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.model.Item

class ItemGridAdapter(
    private var items: List<Item>,
    private val onItemClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemGridAdapter.ItemViewHolder>() {

    fun updateItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.itemIcon)
        val name: TextView = itemView.findViewById(R.id.itemName)
        init {
            itemView.setOnClickListener {
                val item = items[adapterPosition]
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_grid, parent, false)
        return ItemViewHolder(view)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        Glide.with(holder.icon).load(item.imageUrl).into(holder.icon)
        holder.name.text = item.name
    }
}

