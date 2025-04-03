package com.example.yumi2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R

class NameSearchAdapter(
    private val recentSearchList: MutableList<String>,       // 최근 검색어 목록
    private val onItemClick: (String) -> Unit,               // 항목 클릭 시 동작
    private val onDeleteClick: (String) -> Unit              // 항목 삭제 시 동작
) : RecyclerView.Adapter<NameSearchAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val searchText: TextView = itemView.findViewById(R.id.recentSearchItemText)
        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_name_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = recentSearchList[position]
        holder.searchText.text = item

        // 아이템 전체 클릭 -> 해당 검색어로 검색하도록
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // X 버튼 클릭 -> 해당 검색어 삭제
        holder.deleteButton.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = recentSearchList.size

    // 목록에서 특정 아이템 삭제 후 갱신하는 헬퍼 함수
    fun removeItem(item: String) {
        val index = recentSearchList.indexOf(item)
        if (index != -1) {
            recentSearchList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    // 전체 삭제
    fun clearAll() {
        recentSearchList.clear()
        notifyDataSetChanged()
    }
}
