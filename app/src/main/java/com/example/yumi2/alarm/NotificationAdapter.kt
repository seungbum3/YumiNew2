package com.example.yumi2.alarm

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.ChatActivity
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

        // 알림 종류별로 제목 다르게 표시
        holder.title.text = when (n.type) {
            "chat" -> "채팅이 도착했습니다"
            "reply" -> "답글을 달았습니다"
            else -> "댓글을 달았습니다"
        }
        holder.time.text = getRelativeTime(n.timestamp)

        holder.profileImage.setOnClickListener {
            val intent = Intent(ctx, MyPageActivity::class.java)
            intent.putExtra("uid", n.senderUid)
            ctx.startActivity(intent)
        }
        holder.senderNickname.setOnClickListener {
            val intent = Intent(ctx, MyPageActivity::class.java)
            intent.putExtra("uid", n.senderUid)
            ctx.startActivity(intent)
        }

        // 3. 알림 전체 클릭
        holder.itemView.setOnClickListener {
            if (n.type == "chat" && n.chatId != null) {
                // 채팅 알림일 때 → 채팅방으로 이동
                val intent = Intent(ctx, ChatActivity::class.java).apply {
                    putExtra("chatId", n.chatId)
                    putExtra("friendId", n.senderUid)
                    putExtra("friendNickname", n.senderNickname)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                ctx.startActivity(intent)
            } else {
                // 댓글/답글 알림일 때 → 게시글로 이동
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    putExtra("targetPostId", n.postId)
                    n.commentId?.let { cid -> putExtra("targetCommentId", cid) }
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                ctx.startActivity(intent)
            }
        }
    }

}
