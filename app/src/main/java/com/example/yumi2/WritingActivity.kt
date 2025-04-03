package com.example.yumi2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class WritingActivity : AppCompatActivity() {

    private val PICK_IMAGE_REQUEST = 100
    private var imageUri: Uri? = null
    private lateinit var currentCategory: String
    private lateinit var selectedImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writing)

        currentCategory = intent.getStringExtra("category") ?: "자유"

        val titleEditText = findViewById<EditText>(R.id.editText)
        val contentEditText = findViewById<EditText>(R.id.editTextContent)
        val saveButton = findViewById<Button>(R.id.button)
        selectedImageView = findViewById(R.id.imageView)

        // 이미지 선택 이벤트 추가
        selectedImageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // 게시글 저장 버튼 클릭 이벤트 추가
        saveButton.setOnClickListener {
            val title = titleEditText.text.toString()
            val content = contentEditText.text.toString()

            if (imageUri != null) {
                // 이미지가 있으면 Firebase Storage에 업로드 후 Firestore에 저장
                uploadImageToFirebase(imageUri!!) { imageUrl ->
                    savePostToFirestore(title, content, imageUrl)
                }
            } else {
                // 이미지가 없으면 그냥 Firestore에 저장
                savePostToFirestore(title, content, null)
            }
        }

        // 뒤로 가기 버튼 처리
        val backButton = findViewById<ImageView>(R.id.imageView3)
        backButton.setOnClickListener {
            finish()
        }
    }

    // 이미지 선택 후 처리하는 함수
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            imageUri = data?.data
            selectedImageView.setImageURI(imageUri)
        }
    }

    // 이미지를 Firebase Storage에 업로드하는 함수
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

    private fun savePostToFirestore(title: String, content: String, imageUrl: String?) {
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document()

        // 현재 로그인한 사용자와 UID 가져오기
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val userUid = user.uid

        // user_profiles에서 nickname 가져오기
        val profileRef = db.collection("user_profiles").document(userUid)
        profileRef.get().addOnSuccessListener { document ->
            val nickname = document.getString("nickname") ?: "닉네임 없음"

            // uid와 nickname을 포함하여 글 데이터 구성
            val postMap = hashMapOf(
                "title" to title,
                "content" to content,
                "category" to currentCategory,
                "timestamp" to System.currentTimeMillis(),
                "views" to 0,
                "postId" to postRef.id,
                "imageUrl" to imageUrl,
                "uid" to userUid,
                "nickname" to nickname
            )

            postRef.set(postMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글 저장됨", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "닉네임 불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }
}

