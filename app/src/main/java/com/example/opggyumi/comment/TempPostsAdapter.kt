package com.example.opggyumi.comment

import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import com.google.gson.Gson

// posts를 MutableList로 받고, 삭제 시 호출할 콜백 함수를 추가합니다.
class TempPostsAdapter(
    private val posts: MutableList<Post>,
    private val onDelete: (Post) -> Unit
) : RecyclerView.Adapter<TempPostsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // item_post.xml의 제목 TextView를 사용합니다.
        val titleTextView: TextView = itemView.findViewById(R.id.text_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // item_post.xml 레이아웃을 재활용하여 임시 저장된 글 목록을 표시합니다.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        holder.titleTextView.text = post.title

        // 아이템 클릭 시 "삭제"와 "수정" 옵션을 제공하는 다이얼로그를 띄웁니다.
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
