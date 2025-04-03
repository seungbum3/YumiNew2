package com.example.yumi2.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.BanPickChampionChoice
import com.example.yumi2.R
import com.example.yumi2.model.ChampionData
import com.squareup.picasso.Picasso

class ChampionAdapter(
    private val onItemClick: (ChampionData) -> Unit
) : ListAdapter<ChampionData, ChampionAdapter.ChampionViewHolder>(ChampionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChampionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_champion, parent, false)
        return ChampionViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ChampionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChampionViewHolder(
        itemView: View,
        private val onItemClick: (ChampionData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val championImage: ImageView = itemView.findViewById(R.id.championImage)
        private val championName: TextView = itemView.findViewById(R.id.championName)
        private val championOverlay: ImageView = itemView.findViewById(R.id.championOverlay)

        fun bind(champion: ChampionData) {
            // 이미지와 이름 표시
            Picasso.get().load(champion.iconUrl).into(championImage)
            championName.text = champion.name

            val isPicked = BanPickChampionChoice.selectedChampions.contains(champion.id)

            if (isPicked) {
                // 선택된 챔피언 → 흐리게 + X 표시 + 클릭 막기
                championOverlay.visibility = View.VISIBLE
                itemView.alpha = 1.0f
                itemView.isEnabled = false
            } else {
                // 선택되지 않은 챔피언
                championOverlay.visibility = View.GONE
                itemView.alpha = 1.0f
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
