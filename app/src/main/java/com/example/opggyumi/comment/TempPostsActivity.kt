package com.example.opggyumi.comment

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opggyumi.R
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

        loadTempPosts()
    }

    private fun loadTempPosts() {
        // SharedPreferences에서 임시 저장된 글 목록 불러오기
        val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString("posts", null)
        val type = object : TypeToken<MutableList<Post>>() {}.type
        val posts: MutableList<Post> = if (json != null) gson.fromJson(json, type) else mutableListOf()

        tempPosts.clear()
        tempPosts.addAll(posts)
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
