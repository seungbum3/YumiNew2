package com.example.opggyumi.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.nicknameText.text = comment.nickname ?: "알 수 없음"
        holder.commentText.text = comment.text
        // 여기서 기존 날짜 포맷팅 대신 getRelativeTime() 함수 사용
        holder.commentTimestamp.text = getRelativeTime(comment.timestamp)

        // "답글달기" 버튼 처리
        holder.replyText.visibility = View.VISIBLE
        holder.replyText.setOnClickListener {
            replyClickListener.invoke(comment)
        }

        // 답글들을 replyContainer에 동적으로 추가
        holder.replyContainer.removeAllViews()  // 기존 답글 초기화
        if (comment.replies.isNotEmpty()) {
            for (reply in comment.replies) {
                val replyView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_reply, holder.replyContainer, false)
                val replyNickname = replyView.findViewById<TextView>(R.id.replyNickname)
                val replyText = replyView.findViewById<TextView>(R.id.replyText)
                val replyTimestamp = replyView.findViewById<TextView>(R.id.replyTimestamp)

                replyNickname.text = reply.nickname ?: "알 수 없음"
                replyText.text = reply.text
                replyTimestamp.text = getRelativeTime(reply.timestamp) // 상대 시간 표시

                holder.replyContainer.addView(replyView)
            }
            holder.replyContainer.visibility = View.VISIBLE
        } else {
            holder.replyContainer.visibility = View.GONE
        }
    }


    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nicknameText: TextView = itemView.findViewById(R.id.commentNickname)
        val commentText: TextView = itemView.findViewById(R.id.commentText)
        val commentTimestamp: TextView = itemView.findViewById(R.id.commentTimestamp)
        val replyText: TextView = itemView.findViewById(R.id.text_reply)
        // replyContainer는 item_comment.xml 내에 답글들을 담을 LinearLayout의 ID입니다.
        val replyContainer: LinearLayout = itemView.findViewById(R.id.replyContainer)
    }
}
