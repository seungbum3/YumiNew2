package com.example.yumi2.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R
import java.util.*

class CommentAdapter(
    private val comments: List<Comment>,
    private val replyClickListener: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {
    // 맨 위 클래스 정의부분에 추가
    var onAddFriendClick: ((uid: String, nickname: String?) -> Unit)? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.nicknameText.text = comment.nickname ?: "알 수 없음"
        holder.commentText.text = comment.text
        holder.commentTimestamp.text = getRelativeTime(comment.timestamp)

        // "답글달기" 버튼 처리
        holder.replyText.visibility = View.VISIBLE
        holder.replyText.setOnClickListener {
            replyClickListener.invoke(comment)
        }
        holder.optionsButton.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.comment_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_add_friend -> {
                        // 콜백 실행 (3번에서 이 함수 구현)
                        if (comment.uid != null) {
                            onAddFriendClick?.invoke(comment.uid, comment.nickname)
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }


        // 1️⃣ 닉네임 클릭 시 프로필 화면 이동
        holder.nicknameText.setOnClickListener {
            val context = holder.itemView.context
            val intent = android.content.Intent(context, com.example.yumi2.MyPageActivity::class.java)
            intent.putExtra("uid", comment.uid)  // <- comment.uid로 유저 UID 전달
            context.startActivity(intent)
        }

        // 답글 동적 추가 부분은 기존과 동일
        holder.replyContainer.removeAllViews()
        if (comment.replies.isNotEmpty()) {
            for (reply in comment.replies) {
                val replyView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_reply, holder.replyContainer, false)
                val replyNickname = replyView.findViewById<TextView>(R.id.replyNickname)
                val replyText = replyView.findViewById<TextView>(R.id.replyText)
                val replyTimestamp = replyView.findViewById<TextView>(R.id.replyTimestamp)

                replyNickname.text = reply.nickname ?: "알 수 없음"
                replyText.text = reply.text
                replyTimestamp.text = getRelativeTime(reply.timestamp)

                // 2️⃣ 답글 닉네임도 프로필 이동 지원
                replyNickname.setOnClickListener {
                    val context = holder.itemView.context
                    val intent = android.content.Intent(context, com.example.yumi2.MyPageActivity::class.java)
                    intent.putExtra("uid", reply.uid)  // <- reply.uid로 유저 UID 전달
                    context.startActivity(intent)
                }

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
        val optionsButton: android.widget.ImageView = itemView.findViewById(R.id.commentOptions)
    }
}
