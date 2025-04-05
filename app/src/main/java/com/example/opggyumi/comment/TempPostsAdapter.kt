package com.example.opggyumi.comment

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class TempPostsAdapter(
    private val posts: MutableList<Post>,
    private val onDelete: (Post) -> Unit
) : RecyclerView.Adapter<TempPostsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // item_post.xml의 각 TextView들을 참조합니다.
        val titleTextView: TextView = itemView.findViewById(R.id.text_title)
        val nicknameTextView: TextView = itemView.findViewById(R.id.text_nickname)
        val categoryTextView: TextView = itemView.findViewById(R.id.text_category)
        val timestampTextView: TextView = itemView.findViewById(R.id.text_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        // 임시저장창에서만 적용할 크기 변경: 예를 들어 높이를 230픽셀로 변경
        val params = view.layoutParams
        params.height = 230  // 원하는 높이로 변경 (픽셀 단위)
        view.layoutParams = params
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        // 제목, 카테고리, 타임스탬프 할당
        holder.titleTextView.text = post.title
        holder.categoryTextView.text = post.category
        holder.timestampTextView.text = getRelativeTime(post.timestamp)

        // Firestore에서 uid를 이용해 닉네임 가져오기
        if (post.uid != null) {
            FirebaseFirestore.getInstance().collection("user_profiles")
                .document(post.uid)
                .get()
                .addOnSuccessListener { document ->
                    val nickname = document.getString("nickname") ?: "알 수 없음"
                    holder.nicknameTextView.text = nickname
                }
                .addOnFailureListener {
                    holder.nicknameTextView.text = "알 수 없음"
                }
        } else {
            holder.nicknameTextView.text = "알 수 없음"
        }

        // 아이템 클릭 시 "삭제"와 "수정" 옵션을 제공하는 다이얼로그 띄우기
        holder.itemView.setOnClickListener {
            val options = arrayOf("삭제", "수정")
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("옵션 선택")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> { // 삭제 선택: 삭제 콜백 호출
                            onDelete(post)
                        }
                        1 -> { // 수정 선택: WritingActivity로 데이터 전달
                            val intent = Intent(holder.itemView.context, WritingActivity::class.java)
                            // Gson을 사용하여 Post 객체를 JSON 문자열로 전달합니다.
                            intent.putExtra("temp_post", Gson().toJson(post))
                            intent.putExtra("category", post.category)  // 게시글에 저장된 카테고리 정보 전달
                            holder.itemView.context.startActivity(intent)
                        }
                    }
                }
                .show()
        }
    }

    override fun getItemCount(): Int = posts.size
}
