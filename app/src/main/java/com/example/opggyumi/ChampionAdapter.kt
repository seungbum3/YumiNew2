package com.example.opggyumi.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import com.example.opggyumi.api.ChampionData
import com.squareup.picasso.Picasso

class ChampionAdapter(private val championList: List<ChampionData>) : RecyclerView.Adapter<ChampionAdapter.ChampionViewHolder>() {

    class ChampionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val championImage: ImageView = view.findViewById(R.id.championImage)
        val championName: TextView = view.findViewById(R.id.championName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChampionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_champion, parent, false)
        return ChampionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChampionViewHolder, position: Int) {
        val champion = championList[position]

        // 🔹 로그 추가
        Log.d("Adapter", "현재 바인딩 중인 챔피언: ${champion.name}, ${champion.imageUrl}")

        Picasso.get().load(champion.imageUrl).into(holder.championImage) // 🔹 이미지 URL 변경
        holder.championName.text = champion.name // 🔹 챔피언 이름 표시
    }

    override fun getItemCount() = championList.size
}
