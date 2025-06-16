package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.example.yumi2.viewmodel.RegisterViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Locale

class JoinActivity : AppCompatActivity() {
    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var isNicknameAvailable = false

    // 닉네임 중복 검사: users 컬렉션에서 닉네임 필드로 체크
    private fun checkNicknameDuplicate(nickname: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo("nickname", nickname)
            .get()
            .addOnSuccessListener { documents -> callback(documents.isEmpty) }
            .addOnFailureListener { e ->
                Toast.makeText(this, "닉네임 중복 확인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.join)

        auth = FirebaseAuth.getInstance()

        findViewById<Button>(R.id.PageBack).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.btnGoToLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        val passwordInput = findViewById<EditText>(R.id.editTextPassword)
        val nicknameInput = findViewById<EditText>(R.id.editTextNickname)
        val registerButton = findViewById<Button>(R.id.btnLogin)
        val verifyEmailButton = findViewById<Button>(R.id.VerifyEmailbtn)
        val nameCheckButton = findViewById<Button>(R.id.NameCheckbtn)

        emailInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                verifyEmailButton.isEnabled = Patterns.EMAIL_ADDRESS
                    .matcher(s.toString().trim()).matches()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        nameCheckButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()
            if (nickname.isEmpty()) {
                Toast.makeText(this, "닉네임을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            checkNicknameDuplicate(nickname) { isAvailable ->
                isNicknameAvailable = isAvailable
                Toast.makeText(
                    this,
                    if (isAvailable) "사용 가능한 닉네임입니다." else "이미 사용 중인 닉네임입니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        verifyEmailButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            when (verifyEmailButton.text.toString()) {
                "인증하기" -> {
                    // 1) 가입 차단 리스트 확인
                    db.collection("banned_emails").document(email)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                Toast.makeText(
                                    this,
                                    "탈퇴된 이메일로는 재가입할 수 없습니다.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                // 2) Firebase Auth 이메일 존재 확인
                                auth.fetchSignInMethodsForEmail(email)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val methods = task.result?.signInMethods
                                            if (!methods.isNullOrEmpty()) {
                                                Toast.makeText(
                                                    this,
                                                    "이미 가입된 이메일입니다.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                val actualPassword = passwordInput.text.toString().trim()
                                                if (actualPassword.isEmpty()) {
                                                    Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                                                    return@addOnCompleteListener
                                                }
                                                sendEmailVerification(email, actualPassword)
                                                verifyEmailButton.text = "인증 확인"
                                            }
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "이메일 중복 확인 오류: ${task.exception?.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "가입 전 확인 실패: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                "인증 확인" -> {
                    auth.currentUser?.reload()?.addOnCompleteListener { task ->
                        if (task.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                            verifyEmailButton.text = "인증 완료"
                            verifyEmailButton.isEnabled = false
                            Toast.makeText(this, "이메일 인증이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "이메일 인증이 아직 완료되지 않았습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // 인증 완료 뒤 동작 없음
            }
        }

        registerButton.setOnClickListener {
            // 회원가입 전 모든 검증 로직(입력, 인증, 닉네임, 등)이 완료된 상태여야 함
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null || !user.isEmailVerified) {
                Toast.makeText(this, "이메일 인증을 먼저 완료하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val nickname = nicknameInput.text.toString().trim()
            if (nickname.isEmpty() || !isNicknameAvailable) {
                Toast.makeText(this, "유효한 닉네임을 사용하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Firestore에 사용자 정보 저장
            saveUserToFirestore(
                user.uid,
                user.email ?: "",
                passwordInput.text.toString().trim(),
                nickname
            )
        }
    }

    private fun sendEmailVerification(email: String, actualPassword: String) {
        auth.createUserWithEmailAndPassword(email, actualPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "이메일 인증 링크를 보냈습니다. 이메일에서 확인 후 진행하세요.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "인증 이메일 전송 실패: ${verifyTask.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(
                        this,
                        "계정 생성 실패: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun saveUserToFirestore(
        uid: String,
        email: String,
        password: String,
        nickname: String
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(System.currentTimeMillis())

        val userMap = hashMapOf(
            "email" to email,
            "password" to password,
            "uid" to uid,
            "createdAt" to currentTime
        )
        val userProfileMap = hashMapOf(
            "nickname" to nickname,
            "myinfo" to "아직 자기소개가 없습니다.",
            "theme" to "default",
            "profileImageUrl" to "gs://yumi-5f5c0.firebasestorage.app/default_profile.jpg"
        )

        db.collection("users").document(uid)
            .set(userMap)
            .addOnSuccessListener {
                db.collection("user_profiles").document(uid)
                    .set(userProfileMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "user_profiles 저장 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "회원가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
