package com.example.yumi2.viewmodel

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.BanPickChampionChoice
import com.example.yumi2.R
import com.example.yumi2.api.ChampionData
import com.squareup.picasso.Picasso

class ChampionViewHolder(
    itemView: View,
    private val onItemClick: (ChampionData) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private val championImage: ImageView = itemView.findViewById(R.id.championImage)
    private val championName: TextView = itemView.findViewById(R.id.championName)
    private val championOverlay: ImageView = itemView.findViewById(R.id.championOverlay)

    fun bind(champion: ChampionData) {
        Picasso.get().load(champion.imageUrl).into(championImage)
        championName.text = champion.name

        val isPicked = BanPickChampionChoice.selectedChampions.contains(champion.id ?: "")

        if (isPicked) {
            championOverlay.visibility = View.VISIBLE
            itemView.alpha = 0.4f
            itemView.isEnabled = false
        } else {
            championOverlay.visibility = View.GONE
            itemView.alpha = 1.0f
            itemView.isEnabled = true
            itemView.setOnClickListener {
                onItemClick(champion)
            }
        }
    }
}
