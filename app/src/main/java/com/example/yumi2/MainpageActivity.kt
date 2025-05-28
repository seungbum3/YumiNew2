package com.example.yumi2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.alarm.NotificationActivity
import com.example.yumi2.comment.MainActivity
import com.example.yumi2.comment.PostDetailFragment
import com.example.yumi2.viewmodel.ChampionViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class MainpageActivity : AppCompatActivity() {
    private val viewModel: ChampionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationIcon: ImageView

    private val firestore = FirebaseFirestore.getInstance()
    private val CHANNEL_ID = "comment_notifications"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mainpage)

        // 딥링크 인텐트 처리: 알림 클릭 시 특정 게시글/댓글로 이동
        intent.getStringExtra("targetPostId")?.let { postId ->
            val commentId = intent.getStringExtra("targetCommentId")
            openPostDetail(postId, commentId)
        }

        // 요청: Android 13+ 알림 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        createNotificationChannel()
        listenForNewNotifications()

        notificationIcon = findViewById(R.id.notificationIcon)
        notificationIcon.setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        // ─── 기존 코드 ───
        val noticeButton: Button = findViewById(R.id.notice)
        noticeButton.setOnClickListener {
            val url =
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-2025-s1-3-notes/"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        findViewById<Button>(R.id.NameSearch).setOnClickListener {
            startActivity(Intent(this, NameSearchActivity::class.java))
        }

        recyclerView = findViewById(R.id.championRecyclerView)
        recyclerView.layoutManager =
            GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false)

        viewModel.championList.observe(this) { championList ->
            Log.d("RecyclerView", "챔피언 리스트 업데이트됨: $championList")
            recyclerView.adapter = ChampionAdapter(championList)
        }
        viewModel.fetchChampionRotations()

        findViewById<BottomNavigationView>(R.id.bottomNavigation).apply {
            selectedItemId = R.id.category1
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.category1 -> true
                    R.id.category2 -> {
                        startActivity(Intent(this@MainpageActivity, MainActivity::class.java))
                        finish()
                        true
                    }
                    R.id.category3 -> {
                        finish()
                        true
                    }
                    R.id.category4 -> {
                        startActivity(Intent(this@MainpageActivity, MyPageActivity::class.java))
                        finish()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("targetPostId")?.let { postId ->
            val commentId = intent.getStringExtra("targetCommentId")
            openPostDetail(postId, commentId)
        }
    }

    /**
     * 알림 클릭 시, 지정된 게시글 상세로 이동시키는 메서드
     */
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "댓글 알림"
            val desc = "내 게시글에 댓글이 달리면 알려줍니다"
            val chan = NotificationChannel(
                CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = desc }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
    }

    private fun listenForNewNotifications() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection("users")
            .document(myUid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) return@addSnapshotListener
                for (dc in snaps.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val data = dc.document.data
                        val sender = data["senderNickname"] as? String ?: "누군가"
                        val type   = data["type"] as? String ?: "comment"
                        val postId = data["postId"] as? String
                        val commentId = data["commentId"] as? String
                        showLocalNotification(sender, type, postId, commentId)

                    }
                }
            }
    }

    private fun showLocalNotification(sender: String, type: String, postId: String?, commentId: String?) {
        val notificationId = System.currentTimeMillis().toInt()
        val title = if (type == "reply") "$sender 님이 답글을 남겼습니다" else "$sender 님이 댓글을 남겼습니다"
        val body = "앱 내 알림센터에서 확인하세요"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("MainpageActivity", "알림 권한이 없어 로컬 알림을 건너뜁니다")
                return
            }
        }

        val intent = Intent(this, MainpageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            postId?.let { putExtra("targetPostId", it) }
            commentId?.let { putExtra("targetCommentId", it) }
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notif)
        } catch (e: SecurityException) {
            Log.e("MainpageActivity", "알림 전송 실패: 권한 부족", e)
        }
    }

}
