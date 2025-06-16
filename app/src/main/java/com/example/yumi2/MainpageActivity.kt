package com.example.yumi2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.yumi2.model.ChampionData
import com.example.yumi2.MyPageActivity
import com.example.yumi2.adapter.ChampionAdapter
import com.example.yumi2.viewmodel.ChampionViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentChange
import android.Manifest
import android.content.pm.PackageManager
import com.example.yumi2.alarm.NotificationActivity
import com.example.yumi2.comment.MainActivity


class MainpageActivity : AppCompatActivity() {
    private val viewModel: ChampionViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationIcon: ImageView

    private val firestore = FirebaseFirestore.getInstance()
    private val CHANNEL_ID = "comment_notifications"

    override fun onResume() {
        super.onResume()
        com.example.yumi2.alarm.util.AppNotificationManager.loadNotificationSetting(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mainpage)
        SettingsActivity.applyUserTheme(this)

        val rotationButton: Button = findViewById(R.id.Champion_rotation)
        rotationButton.text = "이번주 로테이션 챔피언 ( ${getRotationDateRange()} )"

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

        val noticeButton: Button = findViewById(R.id.notice)

        noticeButton.setOnClickListener {
            val url =
                "https://www.leagueoflegends.com/ko-kr/news/game-updates/patch-2025-s1-3-notes/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val NameSearch: Button = findViewById(R.id.NameSearch)
        NameSearch.setOnClickListener {
            val intent = Intent(this, NameSearchActivity::class.java)
            startActivity(intent)
        }

        val tier_check: Button = findViewById(R.id.tier_check)
        tier_check.setOnClickListener {
            val intent = Intent(this, ChampionTierVowelActivity::class.java)
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.championRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false)


        val adapter = ChampionAdapter {}

        recyclerView.adapter = adapter

        viewModel.championList.observe(this) { championList ->
            Log.d("RecyclerView", "챔피언 리스트 업데이트됨: $championList")
            adapter.submitList(championList)
        }


        viewModel.fetchChampionRotations()

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category1  // '홈'을 기본 선택

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> true  // 현재 페이지이므로 이동하지 않음
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

                R.id.category4 -> {
                    startActivity(Intent(this, MyPageActivity::class.java))
                    finish()
                    true
                }

                else -> false
            }
        }
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
                        val type = data["type"] as? String ?: "comment"
                        showLocalNotification(sender, type)
                    }
                }
            }
    }

    private fun showLocalNotification(sender: String, type: String) {
        val notificationId = System.currentTimeMillis().toInt()
        val title = if (type == "reply") "$sender 님이 답글을 남겼습니다" else "$sender 님이 댓글을 남겼습니다"
        val body = "앱 내 알림센터에서 확인하세요"

        // 권한 확인 (Android 13+)
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

        val intent = Intent(this, NotificationActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }
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

    fun getRotationDateRange(): String {
        val today = java.util.Calendar.getInstance()

        // 이번 주 월요일로 이동
        today.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        val startDate = today.time

        // 다음 주 월요일로 이동
        today.add(java.util.Calendar.DATE, 7)
        val endDate = today.time

        // 날짜 포맷 지정
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return "${sdf.format(startDate)} ~ ${sdf.format(endDate)}"
    }

}
