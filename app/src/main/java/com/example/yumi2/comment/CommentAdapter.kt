package com.example.yumi2.comment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
            val context = view.context
            val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val targetUid = comment.uid ?: return@setOnClickListener

            // 1️⃣ Firestore에서 친구 여부 판별
            FirebaseFirestore.getInstance()
                .collection("users").document(myUid)
                .collection("friends").document(targetUid)
                .get()
                .addOnSuccessListener { doc ->
                    val isFriend = doc.exists()
                    val popup = android.widget.PopupMenu(context, view)
                    if (isFriend) {
                        popup.menuInflater.inflate(R.menu.friend_item_menu, popup.menu)
                    } else {
                        popup.menu.add(0, R.id.action_add_friend, 0, "친구 추가")
                    }

                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.menu_chat -> {
                                getOrCreateChatRoom(myUid, targetUid) { chatId ->
                                    val intent = android.content.Intent(
                                        context,
                                        com.example.yumi2.ChatActivity::class.java
                                    )
                                    intent.putExtra("chatId", chatId)
                                    intent.putExtra("friendId", targetUid)
                                    intent.putExtra("friendNickname", comment.nickname)
                                    context.startActivity(intent)
                                }
                                true
                            }
                            R.id.menu_delete -> {
                                val db = FirebaseFirestore.getInstance()
                                db.collection("users").document(myUid)
                                    .collection("friends").document(targetUid).delete()
                                db.collection("users").document(targetUid)
                                    .collection("friends").document(myUid).delete()
                                Toast.makeText(context, "친구를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                                true
                            }
                            R.id.menu_block -> {
                                val db = FirebaseFirestore.getInstance()
                                // 1. 차단 컬렉션에 추가
                                db.collection("users").document(myUid)
                                    .collection("blocked").document(targetUid)
                                    .set(mapOf("id" to targetUid))
                                    .addOnSuccessListener {
                                        // 2. 친구 목록에서 양쪽 삭제
                                        db.collection("users").document(myUid)
                                            .collection("friends").document(targetUid).delete()
                                        db.collection("users").document(targetUid)
                                            .collection("friends").document(myUid).delete()
                                        Toast.makeText(context, "차단 완료!", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(context, "차단 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                true
                            }
                            R.id.action_add_friend -> {
                                // 친구 추가 코드 (기존)
                                val db = FirebaseFirestore.getInstance()
                                val data = mapOf(
                                    "senderUid" to myUid,
                                    "receiverUid" to targetUid,
                                    "timestamp" to System.currentTimeMillis()
                                )
                                db.collection("users").document(targetUid)
                                    .collection("friend_requests").document(myUid)
                                    .set(data)
                                    .addOnSuccessListener {
                                        db.collection("user_profiles").document(myUid).get()
                                            .addOnSuccessListener { profileDoc ->
                                                val myNickname =
                                                    profileDoc.getString("nickname") ?: "알 수 없음"
                                                val notif = hashMapOf(
                                                    "type" to "friend_request",
                                                    "senderUid" to myUid,
                                                    "senderNickname" to myNickname,
                                                    "timestamp" to System.currentTimeMillis()
                                                )
                                                db.collection("users").document(targetUid)
                                                    .collection("notifications")
                                                    .add(notif)
                                            }
                                            .addOnSuccessListener {
                                                Toast.makeText(
                                                    context,
                                                    "친구 요청을 보냈습니다!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(
                                                    context,
                                                    "친구 요청 전송 실패: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    }
                                true
                            }

                            else -> false
                        }
                    }
                    popup.show()
                }
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
    private fun getOrCreateChatRoom(userA: String, userB: String, callback: (String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val chatsRef = db.collection("chats")
        chatsRef.whereArrayContains("users", userA)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val users = document.get("users") as? List<*>
                    if (users != null && users.contains(userB)) {
                        callback(document.id)
                        return@addOnSuccessListener
                    }
                }
                // 없으면 새로 만듦
                val newChatRef = chatsRef.document()
                val chatData = hashMapOf(
                    "users" to listOf(userA, userB),
                    "lastMessage" to "",
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
                newChatRef.set(chatData)
                    .addOnSuccessListener { callback(newChatRef.id) }
            }
    }

}
