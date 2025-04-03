package com.example.opggyumi.comment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.opggyumi.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class WritingActivity : AppCompatActivity() {

    private val PICK_IMAGE_REQUEST = 100
    private var imageUri: Uri? = null
    private lateinit var currentCategory: String
    private lateinit var selectedImageView: ImageView

    // 해시태그 관련 변수
    private lateinit var hashtagTextView: TextView
    private var hashtagList = arrayListOf<String>()

    // 수정 모드 여부를 판단할 변수 (편집 중인 임시 게시글)
    private var editingTempPost: Post? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writing)

        // 수정 모드 체크: "temp_post" 데이터가 있으면 편집 모드로 간주합니다.
        val tempPostJson = intent.getStringExtra("temp_post")
        editingTempPost = if (!tempPostJson.isNullOrEmpty()) {
            Gson().fromJson(tempPostJson, Post::class.java)
        } else {
            null
        }

        currentCategory = intent.getStringExtra("category") ?: "자유"

        val titleEditText = findViewById<EditText>(R.id.editText)
        val contentEditText = findViewById<EditText>(R.id.editTextContent)
        val saveButton = findViewById<Button>(R.id.button)      // "글 쓰 기" 버튼
        val tempSaveButton = findViewById<Button>(R.id.button2)   // "임시저장" 버튼
        selectedImageView = findViewById(R.id.imageView)
        hashtagTextView = findViewById(R.id.hashtagTextView)

        // 수정 모드인 경우 기존 임시 게시글 데이터를 채워 넣습니다.
        editingTempPost?.let {
            titleEditText.setText(it.title)
            contentEditText.setText(it.content)
            // 이미지나 해시태그 등 다른 필드도 필요한 경우 추가로 설정합니다.
            // 예: if (!it.imageUrl.isNullOrEmpty()) {
            //         selectedImageView.setImageURI(Uri.parse(it.imageUrl))
            //     }
        }

        // 이미지 선택
        selectedImageView.setOnClickListener {
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickIntent, PICK_IMAGE_REQUEST)
        }

        // 해시태그 텍스트뷰 클릭 시 HashtagActivity로 이동
        hashtagTextView.setOnClickListener {
            val intent = Intent(this, HashtagActivity::class.java)
            startActivityForResult(intent, 200)
        }

        // "글 쓰 기" 버튼 클릭 시 Firestore에 게시글 저장
        saveButton.setOnClickListener {
            val title = titleEditText.text.toString()
            val content = contentEditText.text.toString()

            if (imageUri != null) {
                uploadImageToFirebase(imageUri!!) { imageUrl ->
                    savePostToFirestore(title, content, imageUrl)
                }
            } else {
                savePostToFirestore(title, content, null)
            }
        }

        // "임시저장" 버튼 클릭 시 현재 작성 중인 글을 SharedPreferences에 임시 저장
        tempSaveButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val content = contentEditText.text.toString().trim()
            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "제목이나 내용을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newTempPost = Post(
                title = title,
                content = content,
                category = currentCategory,
                timestamp = System.currentTimeMillis(),
                views = 0,
                postId = UUID.randomUUID().toString(),
                imageUrl = imageUri?.toString(),
                uid = FirebaseAuth.getInstance().currentUser?.uid,
                nickname = FirebaseAuth.getInstance().currentUser?.displayName
            )

            // SharedPreferences를 이용하여 임시 저장
            val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = sharedPref.getString("posts", null)
            val type = object : TypeToken<MutableList<Post>>() {}.type
            val posts: MutableList<Post> = if (json != null) gson.fromJson(json, type) else mutableListOf()
            posts.add(newTempPost)
            sharedPref.edit().putString("posts", gson.toJson(posts)).apply()

            Toast.makeText(this, "임시저장 완료", Toast.LENGTH_SHORT).show()
        }

        // 뒤로 가기 버튼 처리
        val backButton = findViewById<ImageView>(R.id.imageView3)
        backButton.setOnClickListener {
            finish()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            selectedImageView.setImageURI(imageUri)
        } else if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            // 해시태그 액티비티에서 반환된 해시태그 목록 받기
            hashtagList = data?.getStringArrayListExtra("hashtags") ?: arrayListOf()
            hashtagTextView.text = hashtagList.joinToString(", ")
        }
    }

    // 예시: 이미지 업로드 함수 (구현 필요)
    private fun uploadImageToFirebase(uri: Uri, callback: (String) -> Unit) {
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("post_images/${System.currentTimeMillis()}.jpg")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                callback(downloadUri.toString())
            }
        }.addOnFailureListener {
            Toast.makeText(this, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // Firestore에 게시글을 저장하는 함수
    private fun savePostToFirestore(title: String, content: String, imageUrl: String?) {
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uid = currentUser.uid

        val profileRef = db.collection("user_profiles").document(uid)
        profileRef.get().addOnSuccessListener { document ->
            val nickname = document.getString("nickname") ?: "닉네임 없음"
            val postMap = hashMapOf(
                "title" to title,
                "content" to content,
                "category" to currentCategory,
                "timestamp" to System.currentTimeMillis(),
                "views" to 0,
                "postId" to postRef.id,
                "imageUrl" to imageUrl,
                "uid" to uid,
                "nickname" to nickname,
                "hashtags" to hashtagList
            )
            postRef.set(postMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글 저장됨", Toast.LENGTH_SHORT).show()
                    // 수정 모드인 경우 SharedPreferences에서 해당 임시 게시글 삭제
                    editingTempPost?.let {
                        removeTempPostFromSharedPref(it.postId)
                    }
                    // MainActivity로 전환하도록 인텐트 호출 (뒤로가기 스택 모두 지움)
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "닉네임 불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // SharedPreferences에서 특정 게시글을 제거하는 함수
    private fun removeTempPostFromSharedPref(postId: String) {
        val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString("posts", null)
        val type = object : TypeToken<MutableList<Post>>() {}.type
        val posts: MutableList<Post> = if (json != null) gson.fromJson(json, type) else mutableListOf()
        val updatedPosts = posts.filter { it.postId != postId }
        sharedPref.edit().putString("posts", gson.toJson(updatedPosts)).apply()
    }
}