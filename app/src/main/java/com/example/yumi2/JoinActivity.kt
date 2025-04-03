package com.example.yumi2

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.yumi2.viewmodel.RegisterViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.viewModels

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
            .addOnSuccessListener { documents ->
                callback(documents.isEmpty)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "닉네임 중복 확인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.join)

        auth = FirebaseAuth.getInstance()

        val PageBack: Button = findViewById(R.id.PageBack)
        PageBack.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        val btnRegister: TextView = findViewById(R.id.btnRegister)
        btnRegister.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // 하나의 입력란으로 이메일을 입력받습니다.
        val emailInput = findViewById<EditText>(R.id.editTextEmail)
        val passwordInput = findViewById<EditText>(R.id.editTextPassword)
        val nicknameInput = findViewById<EditText>(R.id.editTextNickname)
        val registerButton = findViewById<Button>(R.id.btnLogin)
        val verifyEmailButton = findViewById<Button>(R.id.VerifyEmailbtn)
        val nameCheckButton = findViewById<Button>(R.id.NameCheckbtn)

        // 이메일 형식 체크: 이메일 형식이 맞으면 인증 버튼 활성화
        emailInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val email = s.toString().trim()
                verifyEmailButton.isEnabled = Patterns.EMAIL_ADDRESS.matcher(email).matches()
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
                if (isAvailable) {
                    Toast.makeText(this, "사용 가능한 닉네임입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // "인증하기" 버튼 클릭 시, 중복 확인 후 이메일 인증 링크 전송
        verifyEmailButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일 주소를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            when (verifyEmailButton.text.toString()) {
                "인증하기" -> {
                    auth.fetchSignInMethodsForEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val signInMethods = task.result?.signInMethods
                                if (signInMethods != null && signInMethods.isNotEmpty()) {
                                    Toast.makeText(this, "이미 가입된 이메일입니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    // 실제 입력된 비밀번호를 사용하여 계정을 생성
                                    val actualPassword = passwordInput.text.toString().trim()
                                    if (actualPassword.isEmpty()) {
                                        Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                                        return@addOnCompleteListener
                                    }
                                    sendEmailVerification(email, actualPassword)
                                    verifyEmailButton.text = "인증 확인"
                                }
                            } else {
                                Toast.makeText(this, "이메일 중복 확인 오류: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                "인증 확인" -> {
                    auth.currentUser?.reload()?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user?.isEmailVerified == true) {
                                verifyEmailButton.text = "인증 완료"
                                verifyEmailButton.isEnabled = false
                                Toast.makeText(this, "이메일 인증이 완료되었습니다!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "이메일 인증이 아직 완료되지 않았습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                else -> {
                    // 이미 인증 완료 상태라면 아무 동작 없음
                }
            }
        }

        // 회원가입 버튼 클릭 시 모든 필드 확인 후 Firestore에 등록
        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val nickname = nicknameInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || nickname.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "비밀번호는 최소 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 회원가입 전, 현재 계정의 이메일 인증 상태를 확인합니다.
            auth.currentUser?.reload()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null || !user.isEmailVerified) {
                        Toast.makeText(this, "이메일 인증을 완료하세요.", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }
                    // 인증 완료 상태에서 회원가입 진행
                    saveUserToFirestore(user.uid, email, password, nickname)
                } else {
                    Toast.makeText(this, "이메일 인증 상태를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 이메일 인증 링크 전송 (임시 계정 생성 후 전송)
    private fun sendEmailVerification(email: String, actualPassword: String) {
        auth.createUserWithEmailAndPassword(email, actualPassword)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                Toast.makeText(this, "이메일 인증 링크를 보냈습니다. 이메일에서 확인 후 진행하세요.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "인증 이메일 전송 실패: ${verifyTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "계정 생성 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    // Firestore에 사용자 정보 저장 (회원가입)
    private fun saveUserToFirestore(uid: String, email: String, password: String, nickname: String) {
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
                        Toast.makeText(this, "user_profiles 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "회원가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
