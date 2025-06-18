package com.example.yumi2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.ChampionListItem
import com.example.yumi2.R

class ChampionRecyclerAdapter(
    private val items: List<ChampionListItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ChampionRecyclerAdapter.ViewHolder>() {

    private var selectedIndex: Int = -1

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.championGridImage)
        val name: TextView = view.findViewById(R.id.championGridName)
        init {
            view.setOnClickListener {
                onClick(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_champion_grid, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.korName
        if (item.iconUrl.isNotEmpty()) {
            Glide.with(holder.itemView).load(item.iconUrl).into(holder.img)
        } else {
            holder.img.setImageResource(R.drawable.placeholder_image)
        }
        holder.itemView.isSelected = (position == selectedIndex)
    }
    override fun getItemCount() = items.size
}
