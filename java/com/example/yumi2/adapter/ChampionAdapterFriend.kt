package com.example.yumi2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R
import com.example.yumi2.model.ChampionData
import com.squareup.picasso.Picasso

class ChampionAdapterFriend(
    private val selectedChampions: List<String>,  // ✅ 순서 변경: selectedChampions 먼저
    private val onItemClick: (ChampionData) -> Unit
) : ListAdapter<ChampionData, ChampionAdapterFriend.ChampionViewHolder>(ChampionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChampionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_champion, parent, false)
        return ChampionViewHolder(view, selectedChampions, onItemClick)
    }

    override fun onBindViewHolder(holder: ChampionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChampionViewHolder(
        itemView: View,
        private val selectedChampions: List<String>,
        private val onItemClick: (ChampionData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val championImage: ImageView = itemView.findViewById(R.id.championImage)
        private val championName: TextView = itemView.findViewById(R.id.championName)
        private val championOverlay: ImageView = itemView.findViewById(R.id.championOverlay)

        fun bind(champion: ChampionData) {
            Picasso.get().load(champion.iconUrl).into(championImage)
            championName.text = champion.name

            val isPicked = selectedChampions.contains(champion.id)

            if (isPicked) {
                championOverlay.visibility = View.VISIBLE
                championImage.alpha = 0.3f
                itemView.isEnabled = false
            } else {
                championOverlay.visibility = View.GONE
                championImage.alpha = 1.0f
                itemView.isEnabled = true
                itemView.setOnClickListener {
                    onItemClick(champion)
                }
            }
        }
    }

    class ChampionDiffCallback : DiffUtil.ItemCallback<ChampionData>() {
        override fun areItemsTheSame(oldItem: ChampionData, newItem: ChampionData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChampionData, newItem: ChampionData): Boolean {
            return oldItem == newItem
        }
    }
}
