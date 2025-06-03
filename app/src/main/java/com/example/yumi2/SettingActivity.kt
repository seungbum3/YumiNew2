package com.example.yumi2

import com.example.yumi2.alarm.util.AppNotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.yumi2.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val settingsContainer = findViewById<LinearLayout>(R.id.settingsContainer)
        val settingTexts = listOf(
            "로그아웃",
            "친구목록",
            "나만의 아이템 즐겨찾기",
            "챔피언 능력치 계산기",
            "알림 설정",
            "테마 설정",
            "회원탈퇴"
        )

        for (i in settingTexts.indices) {
            val itemView = layoutInflater.inflate(R.layout.item_setting, settingsContainer, false)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dpToPx(6)
            itemView.layoutParams = params

            val itemText = itemView.findViewById<TextView>(R.id.itemText)
            itemText.text = settingTexts[i]

            when (settingTexts[i]) {
                "로그아웃" -> {
                    itemView.setOnClickListener {
                        FirebaseAuth.getInstance().signOut()
                        val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                        sharedPref.edit().remove("loggedInUserId").apply()
                        Intent(this, LoginActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(this)
                        }
                        finish()
                    }
                }
                "알림 설정" -> {
                    itemView.setOnClickListener {
                        showNotificationSettingDialog()  // ★★★ 이 함수는 아래 단계에서 만들 예정!
                    }
                }
                "나만의 아이템 즐겨찾기" -> {
                    itemView.setOnClickListener {
                        startActivity(Intent(this, ItemSelectionActivity::class.java))
                    }
                }
                "챔피언 능력치 계산기" -> {
                    itemView.setOnClickListener {
                        val intent = Intent(this, ChampcalActivity::class.java)
                        startActivity(intent)
                    }
                }
                "친구목록" -> {
                    itemView.setOnClickListener {
                        startActivity(Intent(this, FriendListActivity::class.java))
                    }
                }
                "테마 설정" -> {
                    itemView.setOnClickListener {
                        startActivity(Intent(this, ThemeSettingsActivity::class.java))
                    }
                }
                "회원탈퇴" -> {
                    itemView.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle("회원탈퇴")
                            .setMessage("회원탈퇴 하시겠습니까?")
                            .setNegativeButton("취소", null)
                            .setPositiveButton("탈퇴") { _, _ -> performWithdrawal() }
                            .show()
                    }
                }
                // 필요하다면 다른 항목 추가 처리...
            }

            settingsContainer.addView(itemView)
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
            finish()
        }
    }
    private fun showNotificationSettingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notification_setting, null)
        val switch = dialogView.findViewById<SwitchCompat>(R.id.switchNotification)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Firestore에서 내 알림설정값 불러오기 (없으면 기본값 true)
        FirebaseFirestore.getInstance().collection("user_profiles").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val isOn = doc.getBoolean("notificationOn") ?: true
                switch.isChecked = isOn
            }

        // 2. 스위치 on/off 변경 시 Firestore + 전역 상태 업데이트
        switch.setOnCheckedChangeListener { _, isChecked ->
            com.example.yumi2.alarm.util.AppNotificationManager.setNotificationOn(this, isChecked)
        }

        // 3. 바텀시트 띄우기
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(dialogView)
        sheet.show()
    }

    private fun performWithdrawal() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = user.uid
        val email = user.email ?: ""

        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        // 사용자 데이터 삭제
        db.collection("posts").whereEqualTo("uid", uid).get()
            .addOnSuccessListener { posts ->
                posts.documents.forEach { batch.delete(it.reference) }
                db.collection("comments").whereEqualTo("uid", uid).get()
                    .addOnSuccessListener { comments ->
                        comments.documents.forEach { batch.delete(it.reference) }
                        batch.delete(db.collection("users").document(uid))

                        // 탈퇴 이메일을 차단 리스트에 추가
                        val banRef = db.collection("banned_emails").document(email)
                        batch.set(banRef, mapOf("timestamp" to FieldValue.serverTimestamp()))

                        // 배치 커밋
                        batch.commit().addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                Toast.makeText(this, "데이터 삭제 실패", Toast.LENGTH_LONG).show()
                                return@addOnCompleteListener
                            }
                            // Firebase Auth 계정 삭제
                            user.delete().addOnCompleteListener { authTask ->
                                if (authTask.isSuccessful) {
                                    // 로컬 데이터 정리
                                    getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                        .edit().clear().apply()
                                    // 로그인 화면으로 이동
                                    Intent(this, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }.also { startActivity(it) }
                                    Toast.makeText(this, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "계정 삭제 실패: ${authTask.exception?.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 삭제 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
