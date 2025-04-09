package com.example.yumi2

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class ChampionGridAdapter(private val context: Context, private val champions: List<Pair<String, String>>) : BaseAdapter() {

    private val db = FirebaseFirestore.getInstance()

    override fun getCount(): Int = champions.size

    override fun getItem(position: Int): Any = champions[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: View.inflate(context, R.layout.item_champion_grid, null)
        val imageView = view.findViewById<ImageView>(R.id.championGridImage)
        val textView = view.findViewById<TextView>(R.id.championGridName)
        val champion = champions[position]
        textView.text = champion.second

        db.collection("champions").document(champion.first).get()
            .addOnSuccessListener { document ->
                val url = document.getString("portrait_url")
                if (!url.isNullOrEmpty()) {
                    Glide.with(context).load(url).into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.placeholder_image)
                }
            }
        return view
    }
}
