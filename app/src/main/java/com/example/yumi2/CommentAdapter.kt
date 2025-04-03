package com.example.yumi2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }
    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        // 닉네임 표시
        holder.nicknameText.text = comment.nickname ?: "알 수 없음"
        holder.commentText.text = comment.text
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.commentTimestamp.text = dateFormat.format(Date(comment.timestamp))
    }
    override fun getItemCount(): Int = comments.size
    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nicknameText: TextView = itemView.findViewById(R.id.commentNickname)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
        val commentTimestamp: TextView = itemView.findViewById(R.id.commentTimestamp)
    }
}
