package com.example.yumi2.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.R
import com.example.yumi2.model.ChampionStats

class ChampionStatsAdapter(
    private var items: List<ChampionStats>
) : RecyclerView.Adapter<ChampionStatsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivChampionIcon: ImageView = itemView.findViewById(R.id.ivChampionIcon)
        private val tvChampionName: TextView = itemView.findViewById(R.id.tvChampionName)
        private val tvGames: TextView = itemView.findViewById(R.id.tvGames)
        private val tvWinRate: TextView = itemView.findViewById(R.id.tvWinRate)
        private val tvKills: TextView = itemView.findViewById(R.id.tvKills)
        private val tvDeaths: TextView = itemView.findViewById(R.id.tvDeaths)
        private val tvAssists: TextView = itemView.findViewById(R.id.tvAssists)
        private val tvCS: TextView = itemView.findViewById(R.id.tvCS)
        private val tvGold: TextView = itemView.findViewById(R.id.tvGold)

        fun bind(data: ChampionStats) {
            tvChampionName.text = data.championName
            tvGames.text = data.games.toString()
            tvWinRate.text = String.format("%.2f%%", data.winRate)
            tvKills.text = String.format("%.1f", data.avgKills)
            tvDeaths.text = String.format("%.1f", data.avgDeaths)
            tvAssists.text = String.format("%.1f", data.avgAssists)
            tvCS.text = String.format("%.1f", data.avgCS)
            tvGold.text = String.format("%.1f", data.avgGold)

            val version = "13.5.1"
            val iconUrl = "https://ddragon.leagueoflegends.com/cdn/$version/img/champion/${data.championEngId}.png"
            Glide.with(itemView.context)
                .load(iconUrl)
                .error(R.drawable.error_image)
                .into(ivChampionIcon)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_champion_stats, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    // 아이템 리스트 갱신용
    fun setItems(newItems: List<ChampionStats>) {
        Log.d("ChampionStatsAdapter", "setItems: newItems.size=${newItems.size}")
        items = newItems
        notifyDataSetChanged()
    }
}