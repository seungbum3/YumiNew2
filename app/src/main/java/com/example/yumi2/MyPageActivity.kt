package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.yumi2.comment.MainActivity
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


        // 마이페이지에서 큼지막한 버튼3개
        // onCreate 내부에 추가
        findViewById<CardView>(R.id.btnMyItem).setOnClickListener {
            // 나만의 즐겨찾기 아이템 페이지로 이동
            startActivity(Intent(this, ItemSelectionActivity::class.java))
        }
        findViewById<CardView>(R.id.btnChampCalc).setOnClickListener {
            // 챔피언 수치 계산 페이지로 이동
            startActivity(Intent(this, ChampcalActivity::class.java))
        }
        findViewById<CardView>(R.id.btnThird).setOnClickListener {
            // 챔피언 비교 해보기 페이지로 이동
            startActivity(Intent(this, ChampionCompareActivity::class.java))
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

                R.id.category2 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }

                R.id.category3 -> {
                    startActivity(Intent(this, BanPickMain::class.java))
                    finish()
                    true
                }

                R.id.category4 -> true
                else -> false
            }
        }
        findViewById<Button>(R.id.btnViewRecord).setOnClickListener {
            checkAndStartRecordView()
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

        // 👉 친구추가 버튼 표시 로직
        val btnAddFriend = findViewById<Button>(R.id.btnAddFriend)
        btnAddFriend.visibility = View.GONE // 기본은 숨김

        if (targetUid != null && targetUid != myUid && myUid != null) {
            db.collection("users").document(myUid)
                .collection("friends").document(targetUid)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        // 친구가 아니면 버튼 보여주기
                        btnAddFriend.visibility = View.VISIBLE
                        btnAddFriend.setOnClickListener {
                            sendFriendRequest(myUid, targetUid)
                        }
                    } else {
                        btnAddFriend.visibility = View.GONE
                    }
                }
        }
    }

    private fun sendFriendRequest(fromUid: String, toUid: String) {
        val db = FirebaseFirestore.getInstance()
        val data = mapOf(
            "senderUid" to fromUid,
            "receiverUid" to toUid,
            "timestamp" to System.currentTimeMillis()
        )
        // 1. 친구 요청 보내기 (상대방 friend_requests에 저장)
        db.collection("users").document(toUid)
            .collection("friend_requests").document(fromUid)
            .set(data)
            .addOnSuccessListener {
                // 2. 내 닉네임 가져와서 알림까지 추가
                db.collection("user_profiles").document(fromUid).get()
                    .addOnSuccessListener { doc ->
                        val senderNickname = doc.getString("nickname") ?: "알 수 없음"
                        val notif = hashMapOf(
                            "type" to "friend_request",
                            "senderUid" to fromUid,
                            "senderNickname" to senderNickname,
                            "timestamp" to System.currentTimeMillis()
                        )
                        db.collection("users").document(toUid)
                            .collection("notifications")
                            .add(notif)
                    }
                // 안내 메시지(Toast 등)
                runOnUiThread {
                    Toast.makeText(this, "친구 요청을 보냈습니다!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Toast.makeText(this, "친구 요청 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                for (document in documents) {
                    val friendId = document.id
                    val friendData = document.data.toMutableMap()
                    friendData["id"] = friendId
                    friendsList.add(friendData as HashMap<String, String>)
                }
                Log.d("MyPageActivity", "🔵 friendsList: $friendsList") // 추가!

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
    private fun checkAndStartRecordView() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val targetUid = intent.getStringExtra("uid") ?: myUid
        val userProfileRef = FirebaseFirestore.getInstance().collection("user_profiles").document(targetUid)

        userProfileRef.get().addOnSuccessListener { doc ->
            val lolId = doc.getString("lolId")
            if (targetUid == myUid) {
                // 내 프로필
                if (lolId.isNullOrBlank()) {
                    showAddLolIdDialog { newLolId ->
                        userProfileRef.update("lolId", newLolId).addOnSuccessListener {
                            goToNameSearch(newLolId)
                        }
                    }
                } else {
                    goToNameSearch(lolId)
                }
            } else {
                // 남의 프로필
                if (lolId.isNullOrBlank()) {
                    Toast.makeText(this, "전적을 볼 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    goToNameSearch(lolId)
                }
            }
        }
    }


    // LoL 닉네임#태그 입력받는 다이얼로그
    private fun showAddLolIdDialog(onComplete: (String) -> Unit) {
        val editText = android.widget.EditText(this)
        editText.hint = "예) Hide on bush#KR1"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("LoL 닉네임을 입력하세요")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val input = editText.text.toString().trim()
                if (!input.contains("#")) {
                    Toast.makeText(this, "올바른 형식으로 입력하세요", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onComplete(input)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // NameSearchMainActivity로 이동하는 함수
    private fun goToNameSearch(lolId: String) {
        val parts = lolId.split("#")
        val gameName = parts.getOrNull(0) ?: ""
        val tagLine = parts.getOrNull(1) ?: ""
        val intent = Intent(this, NameSearchMainActivity::class.java)
        intent.putExtra("gameName", gameName)
        intent.putExtra("tagLine", tagLine)
        startActivity(intent)
    }

}