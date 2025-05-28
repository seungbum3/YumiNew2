package com.example.yumi2.alarm

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.MyPageActivity
import com.example.yumi2.R
import com.example.yumi2.comment.MainActivity
import com.example.yumi2.model.NotificationItem
import com.example.yumi2.comment.getRelativeTime

class NotificationAdapter(
    private val items: List<NotificationItem>
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val profileImage: ImageView = v.findViewById(R.id.profileImage)
        val senderNickname: TextView = v.findViewById(R.id.tvSenderNickname)
        val title: TextView = v.findViewById(R.id.tvNotificationTitle)
        val time: TextView = v.findViewById(R.id.tvNotificationTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val n = items[pos]
        val ctx = holder.itemView.context

        holder.senderNickname.text = n.senderNickname
        holder.title.text = if (n.type == "reply") "답글을 달았습니다" else "댓글을 달았습니다"
        holder.time.text = getRelativeTime(n.timestamp)

        // 1. 프로필(이미지) 클릭
        holder.profileImage.setOnClickListener {
            val intent = Intent(ctx, MyPageActivity::class.java)
            intent.putExtra("uid", n.senderUid)
            ctx.startActivity(intent)
        }

        // 2. 닉네임 클릭 (닉네임도 프로필 이동 원하면!)
        holder.senderNickname.setOnClickListener {
            val intent = Intent(ctx, MyPageActivity::class.java)
            intent.putExtra("uid", n.senderUid)
            ctx.startActivity(intent)
        }

        // 3. 알림 전체 클릭 (게시글 이동)
        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                putExtra("targetPostId", n.postId)
                n.commentId?.let { cid -> putExtra("targetCommentId", cid) }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            ctx.startActivity(intent)
        }
    }
}
