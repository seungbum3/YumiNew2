package com.example.opggyumi.comment

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TempPostsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TempPostsAdapter
    private val tempPosts = mutableListOf<Post>()

    override fun onResume() {
        super.onResume()
        loadTempPosts()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_temp_posts)


        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // 어댑터 생성 시 삭제 콜백을 전달합니다.
        adapter = TempPostsAdapter(tempPosts) { post ->
            onDeleteTempPost(post)
        }
        recyclerView.adapter = adapter
        // 뒤로가기 버튼 이벤트 설정
        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener {
            finish() // 현재 액티비티 종료
        }
        loadTempPosts()
    }
    private fun loadTempPosts() {
        // SharedPreferences에서 임시 저장된 글 목록 불러오기
        val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString("posts", null)
        val type = object : TypeToken<MutableList<Post>>() {}.type
        val posts: MutableList<Post> = if (json != null) gson.fromJson(json, type) else mutableListOf()

        // 최신순으로 정렬 (timestamp 기준 내림차순)
        posts.sortByDescending { it.timestamp }

        // 현재 로그인한 사용자의 uid와 일치하는 게시글만 추가 (필요한 경우)
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        tempPosts.clear()
        if (currentUserUid != null) {
            tempPosts.addAll(posts.filter { it.uid == currentUserUid })
        } else {
            tempPosts.addAll(posts)
        }
        adapter.notifyDataSetChanged()
    }


    // 삭제 콜백: 해당 게시글을 리스트에서 제거하고 SharedPreferences에 반영
    private fun onDeleteTempPost(post: Post) {
        tempPosts.remove(post)
        val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
        val gson = Gson()
        sharedPref.edit().putString("posts", gson.toJson(tempPosts)).apply()
        adapter.notifyDataSetChanged()
    }
}
