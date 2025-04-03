package com.example.yumi2

import android.content.Context
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

// E7EBED 이게 전체뒷배경
//B1C1CE 이게 강조 배경
//80929F 이게 강강 강조 배경
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
        friendsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        favoritesRecyclerView = findViewById(R.id.favoritesList)
        favoritesRecyclerView.layoutManager = GridLayoutManager(this, 2)
        favoritesAdapter = FavoritesAdapter(mutableListOf(), "")
        favoritesRecyclerView.adapter = favoritesAdapter
    }

    override fun onStart() {
        super.onStart()

        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val uid = sharedPref.getString("loggedInUID", null) ?: ""

        if (uid.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            loadUserProfile(uid)
            loadFriendsList(uid)
            loadFavoritesList(uid)
        }
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
                val db = FirebaseFirestore.getInstance()
                val totalFriends = documents.size()
                if (totalFriends == 0) {
                    runOnUiThread {
                        emptyFriendsText.visibility = View.VISIBLE
                        friendsRecyclerView.visibility = View.GONE
                    }
                    return@addOnSuccessListener
                }
                var fetchedCount = 0
                for (document in documents) {
                    val friendId = document.id
                    val friendMap = hashMapOf<String, String>()
                    friendMap["id"] = friendId

                    // user_profiles에서 친구 정보 조회
                    db.collection("user_profiles").document(friendId)
                        .get()
                        .addOnSuccessListener { profileDoc ->
                            val nickname = profileDoc.getString("nickname") ?: "알 수 없음"
                            val profileImageUrl = profileDoc.getString("profileImageUrl") ?: "default"
                            friendMap["nickname"] = nickname
                            friendMap["profileImageUrl"] = profileImageUrl
                            friendsList.add(friendMap)
                            fetchedCount++
                            if (fetchedCount == totalFriends) {
                                updateFriendsUI(friendsList)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "❌ 프로필 정보 가져오기 실패: $friendId", e)
                            friendMap["nickname"] = "알 수 없음"
                            friendMap["profileImageUrl"] = "default"
                            friendsList.add(friendMap)
                            fetchedCount++
                            if (fetchedCount == totalFriends) {
                                updateFriendsUI(friendsList)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 친구 목록 가져오기 실패", e)
            }
    }

    private fun updateFriendsUI(friendsList: List<HashMap<String, String>>) {
        runOnUiThread {
            if (friendsList.isEmpty()) {
                emptyFriendsText.visibility = View.VISIBLE
                friendsRecyclerView.visibility = View.GONE
            } else {
                emptyFriendsText.visibility = View.GONE
                friendsRecyclerView.visibility = View.VISIBLE
                val friendsAdapter = FriendsAdapter(friendsList, friendsList, R.layout.item_friend)
                friendsRecyclerView.adapter = friendsAdapter
            }
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
