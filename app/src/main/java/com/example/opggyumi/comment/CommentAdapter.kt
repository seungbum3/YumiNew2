package com.example.opggyumi.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(
    private val comments: List<Comment>,
    private val replyClickListener: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.nicknameText.text = comment.nickname ?: "알 수 없음"
        holder.commentText.text = comment.text
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.commentTimestamp.text = dateFormat.format(Date(comment.timestamp))

        // 최상위 댓글에는 "답글달기" 버튼을 노출
        if (true) { // 답글 달기는 최상위 댓글에만 해당되므로 별도의 조건을 둘 수 있음
            holder.replyText.visibility = View.VISIBLE
            holder.replyText.setOnClickListener {
                replyClickListener.invoke(comment)
            }
        } else {
            holder.replyText.visibility = View.GONE
        }

        if (comment.replies.isNotEmpty()) {
            val replyString = comment.replies.joinToString(separator = "\n\n") { reply ->
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = dateFormat.format(Date(reply.timestamp))
                "→ ${reply.nickname}\n      ${reply.text}\n       ${dateStr}"
            }
            holder.replyContent.text = replyString
            holder.replyContent.visibility = View.VISIBLE
        } else {
            holder.replyContent.visibility = View.GONE
        }

    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nicknameText: TextView = itemView.findViewById(R.id.commentNickname)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
        val commentTimestamp: TextView = itemView.findViewById(R.id.commentTimestamp)
        val replyText: TextView = itemView.findViewById(R.id.text_reply) // "답글달기" 버튼
        val replyContent: TextView = itemView.findViewById(R.id.text_reply_content)
    }
}
