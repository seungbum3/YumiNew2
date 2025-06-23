package com.example.yumi2

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class ChampionGridAdapter(
    private val context: Context,
    private val champions: List<ChampionListItem>
) : BaseAdapter() {

    override fun getCount(): Int = champions.size
    override fun getItem(position: Int): Any = champions[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: View.inflate(context, R.layout.item_champion_grid, null)
        val imageView = view.findViewById<ImageView>(R.id.championGridImage)
        val textView = view.findViewById<TextView>(R.id.championGridName)
        val champion = champions[position]
        textView.text = champion.korName

        // 이미지는 champion_choice의 iconUrl
        if (champion.iconUrl.isNotEmpty()) {
            Glide.with(context).load(champion.iconUrl).into(imageView)
        } else {
            imageView.setImageResource(R.drawable.placeholder_image)
        }
        return view
    }
}

