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
        holder.time.text = getRelativeTime(n.timestamp)

        // 1. 알림 종류별 커스텀 텍스트
        when (n.type) {
            "chat" -> {
                // 채팅 내용 7글자만, 초과시 ... 처리
                val msg = n.message ?: ""
                val preview = if (msg.length > 7) msg.take(7) + "..." else msg
                holder.title.text = "\"$preview\" 채팅이 도착했습니다"
            }
            "comment", "reply" -> {
                // postId로 Firestore에서 게시글 제목 가져오기
                if (!n.postId.isNullOrEmpty()) {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("posts").document(n.postId)
                        .get()
                        .addOnSuccessListener { doc ->
                            val postTitle = doc.getString("title") ?: "알 수 없음"
                            holder.title.text = "\"$postTitle\"에서 댓글을 달았습니다"
                        }
                        .addOnFailureListener {
                            holder.title.text = "댓글을 달았습니다"
                        }
                } else {
                    holder.title.text = "댓글을 달았습니다"
                }
            }
            "friend_request" -> {
                holder.title.text = "친구 요청이 도착했습니다"
            }
            else -> {
                holder.title.text = "알림이 도착했습니다"
            }
        }

        // 🔻 클릭 리스너 등은 기존 코드 유지 (아래 그대로)
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

        holder.itemView.setOnClickListener {
            when {
                n.type == "chat" && n.chatId != null -> {
                    val intent = Intent(ctx, ChatActivity::class.java).apply {
                        putExtra("chatId", n.chatId)
                        putExtra("friendId", n.senderUid)
                        putExtra("friendNickname", n.senderNickname)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    ctx.startActivity(intent)
                }
                n.type == "friend_request" -> {
                    val intent = Intent(ctx, com.example.yumi2.FriendRequestActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    ctx.startActivity(intent)
                }
                else -> {
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

}
