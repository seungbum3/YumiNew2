package com.example.yumi2

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.yumi2.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore

class ChatAdapter(
    private var messages: MutableList<ChatMessage>, // ✅ 변경 가능하도록 수정
    private val currentUserId: String
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val profileImage: ImageView? = if (viewType == 1) itemView.findViewById(R.id.profileImage) else null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == 0) R.layout.item_chat_sent else R.layout.item_chat_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view, viewType)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.message

        val previousMessage = if (position > 0) messages[position - 1] else null
        val nextMessage = if (position < messages.size - 1) messages[position + 1] else null

        val isLastInSequence = nextMessage == null || nextMessage.senderId != message.senderId ||
                (nextMessage.timestamp != null && message.timestamp != null &&
                        (nextMessage.timestamp!!.seconds - message.timestamp!!.seconds) > 120) // 2분 이상 차이

        // ✅ profileImage가 존재하는 경우에만 설정 (내가 보낸 메시지는 null)
        holder.profileImage?.let { imageView ->
            if (isLastInSequence && message.senderId != currentUserId) {
                imageView.visibility = View.VISIBLE
                FirebaseFirestore.getInstance().collection("user_profiles")
                    .document(message.senderId)
                    .get()
                    .addOnSuccessListener { document ->
                        val profileUrl = document.getString("profileImageUrl")
                        if (!profileUrl.isNullOrEmpty()) {
                            Glide.with(holder.itemView.context)
                                .load(profileUrl)
                                .circleCrop()
                                .into(imageView)
                        }
                    }
            } else {
                imageView.visibility = View.GONE
            }
        }
    }



    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) 0 else 1
    }

    override fun getItemCount(): Int = messages.size

    // 새로운 메시지 리스트를 추가하는 함수 추가
    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged() // RecyclerView 업데이트
    }
}

