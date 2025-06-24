package com.example.yumi2.comment

import coil.load
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.example.yumi2.Main3Activity
import com.example.yumi2.MainpageActivity
import com.example.yumi2.MyPageActivity
import com.example.yumi2.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var freeBoardAdapter: FreeBoardAdapter
    private lateinit var rankBoardAdapter: RankBoardAdapter
    private lateinit var normalBoardAdapter: NormalBoardAdapter
    private lateinit var championBoardAdapter: ChampionBoardAdapter
    private lateinit var searchEditText: EditText
    private lateinit var currentCategory: String
    private lateinit var latestButton: Button
    private lateinit var popularButton: Button
    private lateinit var allButton: Button
    private var isPopularMode = false

    private val freeBoardList = mutableListOf<Post>()
    private val rankBoardList = mutableListOf<Post>()
    private val normalBoardList = mutableListOf<Post>()
    private val championBoardList = mutableListOf<Post>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appLogo = findViewById<ImageView>(R.id.appLogo)
        appLogo.load(R.drawable.yumi_icon) {
            transformations(RoundedCornersTransformation(15f)) // 10f == 10dp (원하는 만큼 조절)
        }

        searchEditText = findViewById(R.id.search_edit_text)
        latestButton = findViewById(R.id.btn_latest)
        popularButton = findViewById(R.id.btn_popular)
        allButton = findViewById(R.id.btn_all)
        currentCategory = "자유"

        freeBoardAdapter = FreeBoardAdapter(this, freeBoardList)
        rankBoardAdapter = RankBoardAdapter(this, rankBoardList)
        normalBoardAdapter = NormalBoardAdapter(this, normalBoardList)
        championBoardAdapter = ChampionBoardAdapter(this, championBoardList)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = freeBoardAdapter

        val spacingDp = 8 // 원하는 여백(dps)
        val spacingPx = (spacingDp * resources.displayMetrics.density).toInt()
        recyclerView.addItemDecoration(VerticalSpaceItemDecoration(spacingPx))

        findViewById<TextView>(R.id.text_free).setOnClickListener { changeCategory("자유") }
        findViewById<TextView>(R.id.text_rank).setOnClickListener { changeCategory("랭크") }
        findViewById<TextView>(R.id.text_normal).setOnClickListener { changeCategory("일반") }
        findViewById<TextView>(R.id.text_champion).setOnClickListener { changeCategory("챔피언 빌드") }

        allButton.setOnClickListener {
            showAllPosts()
            highlightSortButton(allButton)
        }


        findViewById<Button>(R.id.button3).setOnClickListener {
            val options = arrayOf("새게시글", "임시저장")
            AlertDialog.Builder(this)
                .setTitle("")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val intent = Intent(this, WritingActivity::class.java)
                            intent.putExtra("category", currentCategory)
                            startActivityForResult(intent, 1)
                        }
                        1 -> startActivity(Intent(this, TempPostsActivity::class.java))
                    }
                }
                .show()
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category2
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> {
                    startActivity(Intent(this, MainpageActivity::class.java))
                    finish()
                    true
                }
                R.id.category2 -> true
                R.id.category3 -> {
                    startActivity(Intent(this, Main3Activity::class.java))
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

        latestButton.setOnClickListener {
            isPopularMode = false
            loadPosts()
        }

        popularButton.setOnClickListener {
            isPopularMode = true
            loadPosts()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchPosts(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadPosts()
        // ★ 여기에 추가!
        handleIntent(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        loadPosts()
    }

    private fun showAllPosts() {
        firestore.collection("posts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreError", "전체 게시글 불러오기 실패: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val postList = mutableListOf<Post>()
                    for (document in snapshot.documents) {
                        val title = document.getString("title") ?: ""
                        val content = document.getString("content") ?: ""
                        val category = document.getString("category") ?: ""
                        val timestamp = document.getLong("timestamp") ?: 0L
                        val views = document.getLong("views")?.toInt() ?: 0
                        val postId = document.id
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val uid = document.getString("uid") ?: ""
                        val nickname = document.getString("nickname") ?: ""
                        postList.add(Post(title, content, category, timestamp, views, postId, imageUrl, uid, nickname))
                    }
                    // 최신순으로 정렬
                    postList.sortByDescending { it.timestamp }
                    // 자유게시판 리스트에 보여주게(카테고리 상관없이 전부)
                    freeBoardList.clear()
                    freeBoardList.addAll(postList)
                    freeBoardAdapter.notifyDataSetChanged()
                    // 랭크/일반/챔피언 리스트는 비움
                    rankBoardList.clear()
                    normalBoardList.clear()
                    championBoardList.clear()
                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
                    recyclerView.adapter = freeBoardAdapter
                }
            }
    }

    private fun highlightSortButton(selected: Button) {
        val allButtons = listOf(latestButton, popularButton, allButton)
        for (btn in allButtons) {
            if (btn == selected) {
                btn.setBackgroundTintList(getColorStateList(R.color.selected_tab))
                btn.setTextColor(getColor(R.color.selected_tab_text))
            } else {
                btn.setBackgroundTintList(getColorStateList(R.color.default_tab))
                btn.setTextColor(getColor(R.color.default_tab_text))
            }
        }
    }


    private fun loadPosts() {
        firestore.collection("posts")
            .whereEqualTo("category", currentCategory)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreError", "게시글 불러오기 실패: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val postList = mutableListOf<Post>()
                    for (document in snapshot.documents) {
                        val title = document.getString("title") ?: ""
                        val content = document.getString("content") ?: ""
                        val category = document.getString("category") ?: ""
                        val timestamp = document.getLong("timestamp") ?: 0L
                        val views = document.getLong("views")?.toInt() ?: 0
                        val postId = document.id
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val uid = document.getString("uid") ?: ""
                        val nickname = document.getString("nickname") ?: ""
                        postList.add(Post(title, content, category, timestamp, views, postId, imageUrl, uid, nickname))
                    }

                    // 정렬 기준 적용
                    if (isPopularMode) {
                        postList.sortByDescending { it.views }
                    } else {
                        postList.sortByDescending { it.timestamp }
                    }

                    updateRecyclerView(postList)
                }
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

    // 게시글 상세(댓글 위치)로 이동
    private fun openPostDetail(postId: String, commentId: String?) {
        val frag = PostDetailFragment().apply {
            arguments = Bundle().apply {
                putString("postId", postId)
                commentId?.let { putString("highlightCommentId", it) }
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, frag)
            .addToBackStack(null)
            .commit()
    }

    // 인텐트 딥링크 값 처리
    private fun handleIntent(intent: Intent) {
        val postId = intent.getStringExtra("targetPostId")
        val commentId = intent.getStringExtra("targetCommentId")
        if (!postId.isNullOrEmpty()) {
            openPostDetail(postId, commentId)
        }
    }


    private fun changeCategory(category: String) {
        currentCategory = category
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        when (category) {
            "자유" -> recyclerView.adapter = freeBoardAdapter
            "랭크" -> recyclerView.adapter = rankBoardAdapter
            "일반" -> recyclerView.adapter = normalBoardAdapter
            "챔피언 빌드" -> recyclerView.adapter = championBoardAdapter
        }
        loadPosts()
    }

    private fun searchPosts(query: String) {
        if (query.isEmpty()) {
            loadPosts()
            return
        }
        firestore.collection("posts")
            .get()
            .addOnSuccessListener { result ->
                val filteredList = mutableListOf<Post>()
                for (document in result.documents) {
                    val title = document.getString("title") ?: ""
                    val nickname = document.getString("nickname") ?: ""
                    val content = document.getString("content") ?: ""
                    // 해시태그 필드를 가져와서 리스트로 변환 (존재하지 않으면 빈 리스트)
                    val hashtagsList = document.get("hashtags") as? List<String> ?: emptyList()

                    // 검색어가 제목, 닉네임, 내용 또는 해시태그 목록 중 하나에 포함되어 있는지 확인
                    if (title.contains(query, ignoreCase = true) ||
                        nickname.contains(query, ignoreCase = true) ||
                        content.contains(query, ignoreCase = true) ||
                        hashtagsList.any { it.contains(query, ignoreCase = true) }) {

                        val timestamp = document.getLong("timestamp") ?: 0L
                        val views = document.getLong("views")?.toInt() ?: 0
                        val postId = document.id
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val uid = document.getString("uid") ?: ""
                        val category = document.getString("category") ?: ""

                        val post = Post(
                            title = title,
                            content = content,
                            category = category,
                            timestamp = timestamp,
                            views = views,
                            postId = postId,
                            imageUrl = imageUrl,
                            uid = uid,
                            nickname = nickname
                        )
                        filteredList.add(post)
                    }
                }
                filteredList.sortByDescending { it.timestamp }
                updateRecyclerView(filteredList)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "검색 실패: ${exception.message}")
            }
    }


}
