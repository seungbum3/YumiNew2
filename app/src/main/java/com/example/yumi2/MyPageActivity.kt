package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*

class MyPageActivity : AppCompatActivity(), ProfileEditDialog.ProfileUpdateListener {

    override fun onProfileUpdated(nickname: String, bio: String, imageUrl: String?) {
        refreshProfileUI(nickname, bio, imageUrl)
    }

    private lateinit var emptyFriendsText: TextView
    private lateinit var emptyFavoritesText: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var friendsRecyclerView: RecyclerView
    private lateinit var favoritesRecyclerView: RecyclerView
    private lateinit var favoritesAdapter: FavoritesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mypage)

        emptyFriendsText = findViewById(R.id.emptyFriendsText)
        emptyFavoritesText = findViewById(R.id.emptyFavoritesText)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val settingsText = findViewById<TextView>(R.id.settingsText)
        val settingsIcon = findViewById<ImageView>(R.id.settingsIcon)
        // 1️⃣ 내 uid와, 인텐트로 온 타겟 uid 비교
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val targetUid = intent.getStringExtra("uid")
        val isMyProfile = (targetUid == null || targetUid == myUid)

        // 2️⃣ 내 프로필이 아니면 숨김 (GONE)
        if (isMyProfile) {
            settingsText.visibility = View.VISIBLE
            settingsIcon.visibility = View.VISIBLE
        } else {
            settingsText.visibility = View.GONE
            settingsIcon.visibility = View.GONE
        }
        settingsText.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        settingsIcon.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

                R.id.category2, R.id.category3 -> {
                    finish()
                    true
                }

                R.id.category4 -> true
                else -> false
            }
        }

        val btnProfileEdit = findViewById<Button>(R.id.btnProfileEdit)
        btnProfileEdit.setOnClickListener {
            val dialog = ProfileEditDialog()
            dialog.show(supportFragmentManager, "ProfileEditDialog")
        }

        friendsRecyclerView = findViewById(R.id.friendsList)
        friendsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        favoritesRecyclerView = findViewById(R.id.favoritesList)
        favoritesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        favoritesAdapter = FavoritesAdapter(mutableListOf(), "")
        favoritesRecyclerView.adapter = favoritesAdapter

    }


    override fun onStart() {
        super.onStart()

        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        // 알림에서 넘어온 타겟 uid(=남의 프로필)
        val targetUid = intent.getStringExtra("uid")

        val uidToShow = targetUid ?: myUid

        if (uidToShow.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            loadUserProfile(uidToShow)
            loadFriendsList(uidToShow)
            loadFavoritesList(uidToShow)
        }

        // 프로필 편집 버튼은 내 프로필 볼 때만 보이게!
        val btnProfileEdit = findViewById<Button>(R.id.btnProfileEdit)
        btnProfileEdit.visibility =
            if (targetUid == null || targetUid == myUid) View.VISIBLE else View.GONE
    }


    private fun refreshProfileUI(nickname: String, bio: String, imageUrl: String?) {
        runOnUiThread {
            findViewById<TextView>(R.id.userName).text = nickname
            findViewById<TextView>(R.id.userBio).text = bio
            val profileImageView = findViewById<ImageView>(R.id.profileImage)
            if (!imageUrl.isNullOrEmpty()) {
                if (imageUrl.startsWith("gs://")) {
                    convertGsUrlToHttp(imageUrl) { httpUrl ->
                        loadImage(httpUrl ?: "", profileImageView)
                    }
                } else {
                    loadImage(imageUrl, profileImageView)
                }
            }
        }
    }

    private fun loadImage(url: String?, imageView: ImageView) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .circleCrop()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView)
        }
    }

    private fun loadUserProfile(uid: String) {
        db.collection("user_profiles").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val bio = document.getString("myinfo") ?: "자기소개 없음"
                    val imageUrl = document.getString("profileImageUrl") ?: ""
                    val nickname = document.getString("nickname") ?: "알 수 없음"
                    refreshProfileUI(nickname, bio, imageUrl)
                }
            }
    }

    private fun convertGsUrlToHttp(gsUrl: String, onComplete: (String?) -> Unit) {
        FirebaseStorage.getInstance().getReferenceFromUrl(gsUrl).downloadUrl
            .addOnSuccessListener { downloadUri ->
                onComplete(downloadUri.toString())
            }
            .addOnFailureListener { e ->
                Log.e("URL Conversion", "gs:// URL 변환 실패", e)
                onComplete(null)
            }
    }

    private fun loadFriendsList(uid: String) {
        db.collection("users").document(uid).collection("friends")
            .get()
            .addOnSuccessListener { documents ->
                val friendsList = mutableListOf<HashMap<String, String>>()
                for (document in documents) {
                    val friendId = document.id
                    val friendData = document.data.toMutableMap()
                    friendData["id"] = friendId
                    friendsList.add(friendData as HashMap<String, String>)
                }

                runOnUiThread {
                    if (friendsList.isEmpty()) {
                        emptyFriendsText.visibility = View.VISIBLE
                        friendsRecyclerView.visibility = View.GONE
                    } else {
                        emptyFriendsText.visibility = View.GONE
                        friendsRecyclerView.visibility = View.VISIBLE
                        val friendsAdapter = FriendsAdapter(
                            fullList = friendsList, // 친구 전체 리스트
                            friendsList = friendsList, // 친구 요청 등 필터링용 (필요 없으면 동일 리스트 전달 가능)
                            layoutResId = R.layout.item_friend // 사용하고 있는 친구 아이템 레이아웃
                        )

                        friendsRecyclerView.adapter = friendsAdapter
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 친구 목록 가져오기 실패", e)
            }
    }

    private fun loadFavoritesList(uid: String) {
        db.collection("users").document(uid).collection("favorites")
            .get()
            .addOnSuccessListener { documents ->
                val favoritesList = mutableListOf<HashMap<String, String>>()
                for (document in documents) {
                    val summonerName = document.getString("summonerName") ?: "알 수 없음"
                    favoritesList.add(hashMapOf("summonerName" to summonerName))
                }
                runOnUiThread {
                    if (favoritesList.isEmpty()) {
                        emptyFavoritesText.visibility = View.VISIBLE
                        favoritesRecyclerView.visibility = View.GONE
                    } else {
                        emptyFavoritesText.visibility = View.GONE
                        favoritesRecyclerView.visibility = View.VISIBLE
                        favoritesAdapter.updateFavorites(favoritesList)
                        favoritesRecyclerView.adapter = favoritesAdapter
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 즐겨찾기 목록 가져오기 실패", e)
            }
    }
}