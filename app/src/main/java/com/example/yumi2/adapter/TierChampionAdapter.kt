package com.example.yumi2.adapter

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.ChampionDetailActivity
import com.example.yumi2.R
import com.example.yumi2.model.TierChampion

class TierChampionAdapter(
    private var championList: List<TierChampion>
) : RecyclerView.Adapter<TierChampionAdapter.TierViewHolder>() {

    inner class TierViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val imgChampionIcon: ImageView = itemView.findViewById(R.id.imgChampionIcon)
        val tvChampionName: TextView = itemView.findViewById(R.id.tvChampionName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TierViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tier_champion, parent, false)
        return TierViewHolder(view)
    }

    override fun onBindViewHolder(holder: TierViewHolder, position: Int) {
        val champion = championList[position]
        holder.tvRank.text = (position + 1).toString()
        holder.tvChampionName.text = champion.name
        Glide.with(holder.itemView.context)
            .load(champion.iconUrl)
            .placeholder(R.drawable.loading_icon)
            .error(R.drawable.error_image)
            .into(holder.imgChampionIcon)
        holder.itemView.findViewById<TextView>(R.id.tvWinRate).text = "${champion.winRate}%"
        holder.itemView.findViewById<TextView>(R.id.tvPickRate).text = "${champion.pickRate}%"
        holder.itemView.findViewById<TextView>(R.id.tvBanRate).text = "${champion.banRate}%"

        // 로그 출력
        Log.d("TierChampionAdapter", "Binding [${position + 1}] ${champion.name}, icon: ${champion.iconUrl}")
    }

    override fun getItemCount(): Int = championList.size

    fun setItems(newList: List<TierChampion>) {
        championList = newList
        notifyDataSetChanged()
    }
}
