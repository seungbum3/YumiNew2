package com.example.yumi2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class Post(
    val title: String,
    val content: String,
    val category: String,
    val timestamp: Long,
    val views: Int = 0,
    val postId: String,
    val imageUrl: String? = null,
    val uid: String? = null,        // 작성자 UID
    val nickname: String? = null    // 작성자 닉네임
)

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var freeBoardAdapter: FreeBoardAdapter
    private lateinit var rankBoardAdapter: RankBoardAdapter
    private lateinit var normalBoardAdapter: NormalBoardAdapter
    private lateinit var championBoardAdapter: ChampionBoardAdapter
    private lateinit var categoryTitle: TextView
    private lateinit var searchEditText: EditText // 🔍 검색창 추가
    private lateinit var currentCategory: String
    private val freeBoardList = mutableListOf<Post>()
    private val rankBoardList = mutableListOf<Post>()
    private val normalBoardList = mutableListOf<Post>()
    private val championBoardList = mutableListOf<Post>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        categoryTitle = findViewById(R.id.categoryTitle)
        searchEditText = findViewById(R.id.search_edit_text) // 🔍 검색창 초기화
        currentCategory = "자유"
        freeBoardAdapter = FreeBoardAdapter(this, freeBoardList)
        rankBoardAdapter = RankBoardAdapter(this, rankBoardList)
        normalBoardAdapter = NormalBoardAdapter(this, normalBoardList)
        championBoardAdapter = ChampionBoardAdapter(this, championBoardList)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = freeBoardAdapter

        val textFree = findViewById<TextView>(R.id.text_free)
        val textRank = findViewById<TextView>(R.id.text_rank)
        val textNormal = findViewById<TextView>(R.id.text_normal)
        val textChampion = findViewById<TextView>(R.id.text_champion)
        textFree.setOnClickListener { changeCategory("자유") }
        textRank.setOnClickListener { changeCategory("랭크") }
        textNormal.setOnClickListener { changeCategory("일반") }
        textChampion.setOnClickListener { changeCategory("챔피언 빌드") }
        val writeButton = findViewById<Button>(R.id.button3)
        writeButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // 현재 선택된 카테고리를 인텐트에 담아서 글쓰기 화면으로 이동
                val intent = Intent(this, WritingActivity::class.java)
                intent.putExtra("category", currentCategory)
                startActivityForResult(intent, 1)
            } else {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category4

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> {
                    startActivity(Intent(this, MainpageActivity::class.java))
                    finish()
                    true
                }
                R.id.category2 -> true
                R.id.category3 -> {
                    startActivity(Intent(this, BanPickMain::class.java))
                    finish()
                    true
                }
                R.id.category4 -> {
                    startActivity(Intent(this, MyPageActivity::class.java))
                    finish()
                    true
                }
                else -> false

            }
        }
        loadPosts()
        // 🔍 검색 기능 추가
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchPosts(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // MainActivity가 포그라운드로 돌아올 때 최신 데이터를 불러옵니다.
    override fun onResume() {
        super.onResume()
        loadPosts()  // Firestore에서 최신 게시글 데이터를 불러와 조회수 업데이트 반영
    }

    // Firestore에서 게시글 불러오기 (uid와 nickname 포함)
    private fun loadPosts() {
        firestore.collection("posts")
            .whereEqualTo("category", currentCategory)
            .get()
            .addOnSuccessListener { result ->
                val postList = mutableListOf<Post>()
                for (document in result) {
                    val title = document.getString("title") ?: ""
                    val content = document.getString("content") ?: ""
                    val category = document.getString("category") ?: ""
                    val timestamp = document.getLong("timestamp") ?: 0L
                    val views = document.getLong("views")?.toInt() ?: 0
                    val postId = document.id
                    val imageUrl = document.getString("imageUrl") ?: ""
                    // uid와 nickname도 불러옵니다.
                    val uid = document.getString("uid") ?: ""
                    val nickname = document.getString("nickname") ?: ""
                    postList.add(
                        Post(title, content, category, timestamp, views, postId, imageUrl, uid, nickname)
                    )
                }
                // 최신글 순 정렬
                postList.sortByDescending { it.timestamp }
                updateRecyclerView(postList)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "게시글 불러오기 실패: ${exception.message}")
            }
    }

    // 제목 검색 기능
    private fun searchPosts(query: String) {
        if (query.isEmpty()) {
            loadPosts()
            return
        }
        firestore.collection("posts")
            .whereEqualTo("category", currentCategory)
            .get()
            .addOnSuccessListener { result ->
                val filteredList = mutableListOf<Post>()
                for (document in result) {
                    val title = document.getString("title") ?: ""
                    val content = document.getString("content") ?: ""
                    val category = document.getString("category") ?: ""
                    val timestamp = document.getLong("timestamp") ?: 0L
                    val postId = document.id
                    if (title.contains(query, ignoreCase = true)) {
                        filteredList.add(Post(title, content, category, timestamp, postId = postId))
                    }
                }
                updateRecyclerView(filteredList)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "검색 실패: ${exception.message}")
            }
    }

    private fun updateRecyclerView(posts: List<Post>) {
        when (currentCategory) {
            "자유" -> {
                freeBoardList.clear()
                freeBoardList.addAll(posts)
                freeBoardAdapter.notifyDataSetChanged()
            }
            "랭크" -> {
                rankBoardList.clear()
                rankBoardList.addAll(posts)
                rankBoardAdapter.notifyDataSetChanged()
            }
            "일반" -> {
                normalBoardList.clear()
                normalBoardList.addAll(posts)
                normalBoardAdapter.notifyDataSetChanged()
            }
            "챔피언 빌드" -> {
                championBoardList.clear()
                championBoardList.addAll(posts)
                championBoardAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun changeCategory(category: String) {
        currentCategory = category
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        when (category) {
            "자유" -> {
                recyclerView.adapter = freeBoardAdapter
                categoryTitle.text = "자유 게시판"
            }
            "랭크" -> {
                recyclerView.adapter = rankBoardAdapter
                categoryTitle.text = "랭크 게시판"
            }
            "일반" -> {
                recyclerView.adapter = normalBoardAdapter
                categoryTitle.text = "일반 게시판"
            }
            "챔피언 빌드" -> {
                recyclerView.adapter = championBoardAdapter
                categoryTitle.text = "챔피언 빌드 게시판"
            }
        }
        loadPosts()
    }
}
