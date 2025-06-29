package com.example.yumi2.comment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.yumi2.ItemSelectionActivity
import com.example.yumi2.R
import com.example.yumi2.model.Item
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
    private var selectedItems: List<Item?> = emptyList()
    companion object {
        const val ITEM_SELECTION_REQUEST_CODE = 1010
    }

    // 해시태그 관련 변수
    private lateinit var hashtagTextView: TextView
    private var hashtagList = arrayListOf<String>()
    private var selectedChampionId: String? = null  // ← 요거 선언 빠졌어!


    // 편집 모드 여부를 판단할 변수: "temp_post"가 있으면 편집 모드로 간주 (Post 객체)
    private var editingTempPost: Post? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writing)

        // 인텐트에 "temp_post"가 있으면 편집 모드
        val tempPostJson = intent.getStringExtra("temp_post")
        editingTempPost = if (!tempPostJson.isNullOrEmpty()) {
            Gson().fromJson(tempPostJson, Post::class.java)
        } else {
            null
        }

        // 수정 모드라면 기존 게시글의 category를 사용, 아니라면 인텐트에서 전달받거나 기본값 "자유" 사용
        currentCategory = intent.getStringExtra("category") ?: "자유"
        if (editingTempPost != null) {
            currentCategory = editingTempPost!!.category
        }

        val btnSelectItems = findViewById<Button>(R.id.btnSelectItems)
        val textSelectedItems = findViewById<TextView>(R.id.textSelectedItems)

        btnSelectItems.setOnClickListener {
            val intent = Intent(this, ItemSelectionActivity::class.java)
            startActivityForResult(intent, ITEM_SELECTION_REQUEST_CODE)
        }


        val titleEditText = findViewById<EditText>(R.id.editText)
        val contentEditText = findViewById<EditText>(R.id.editTextContent)
        val saveButton = findViewById<Button>(R.id.button)      // "글 쓰 기" 버튼
        val tempSaveButton = findViewById<Button>(R.id.button2)   // "임시저장" 버튼
        selectedImageView = findViewById(R.id.imageView)
        hashtagTextView = findViewById(R.id.hashtagTextView)

        // 편집 모드라면 기존 데이터를 채워 넣음
        editingTempPost?.let {
            titleEditText.setText(it.title)
            contentEditText.setText(it.content)
            hashtagTextView.text = it.hashtags.joinToString(", ")
            // 이미지 등 추가 필드도 필요에 따라 채워 넣으세요.
        }

        // 이미지 선택 버튼
        selectedImageView.setOnClickListener {
            val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(pickIntent, PICK_IMAGE_REQUEST)
        }

        // 해시태그 텍스트뷰 클릭 시 HashtagActivity로 이동
        hashtagTextView.setOnClickListener {
            val intent = Intent(this, HashtagActivity::class.java)
            startActivityForResult(intent, 200)
        }

        saveButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val content = contentEditText.text.toString().trim()

            // 제목, 내용 체크
            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "제목과 내용을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 제목 길이 제한 (예: 10글자 이내)
            if (title.length > 40) {
                Toast.makeText(this, "제목은 10글자 이내여야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 이미지가 선택된 경우: 업로드 후 저장, 아니면 바로 저장
            if (imageUri != null) {
                uploadImageToFirebase(imageUri!!) { imageUrl ->
                    saveOrUpdatePost(title, content, imageUrl)
                }
            } else {
                saveOrUpdatePost(title, content, null)
            }
        }

        // 임시저장 버튼 클릭 시
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
                nickname = FirebaseAuth.getInstance().currentUser?.displayName,
                hashtags = hashtagList
            )
            val sharedPref = getSharedPreferences("temp_posts", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = sharedPref.getString("posts", null)
            val type = object : TypeToken<MutableList<Post>>() {}.type
            val posts: MutableList<Post> = if (json != null) gson.fromJson(json, type) else mutableListOf()
            posts.add(newTempPost)
            sharedPref.edit().putString("posts", gson.toJson(posts)).apply()
            Toast.makeText(this, "임시저장 완료", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // 뒤로가기 버튼 처리
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
            hashtagList = data?.getStringArrayListExtra("hashtags") ?: arrayListOf()
            hashtagTextView.text = hashtagList.joinToString(", ")
        } else if (requestCode == ITEM_SELECTION_REQUEST_CODE && resultCode == RESULT_OK) {
            val itemsJson = data?.getStringExtra("selectedItemsJson")
            if (itemsJson != null) {
                val type = object : TypeToken<List<Item?>>() {}.type
                selectedItems = Gson().fromJson(itemsJson, type)
            }
            selectedChampionId = data?.getStringExtra("championId")

            val names = selectedItems.mapNotNull { it?.name }
            val text = if (names.isEmpty()) "없음" else names.joinToString(", ")
            findViewById<TextView>(R.id.textSelectedItems).text = "아이템: $text"

            // 💡 아이템 이미지 보여주기
            val itemImageContainer = findViewById<LinearLayout>(R.id.itemImageContainer)
            itemImageContainer.removeAllViews()
            for (item in selectedItems) {
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                        setMargins(8, 8, 8, 8)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                Glide.with(this)
                    .load(item?.imageUrl)
                    .placeholder(R.drawable.placeholder_image)
                    .into(imageView)
                itemImageContainer.addView(imageView)
            }
        }

    }

    private fun uploadImageToFirebase(uri: Uri, callback: (String) -> Unit) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("post_images/$currentUid/$fileName")

        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                callback(downloadUri.toString())
            }
        }.addOnFailureListener {
            Toast.makeText(this, "이미지 업로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 글 작성 및 수정 모드에 따라 처리하는 함수
    private fun saveOrUpdatePost(title: String, content: String, imageUrl: String?) {
        if (editingTempPost != null) {
            // 수정 모드이면 업데이트 수행
            updatePost(editingTempPost!!.postId, title, content, imageUrl)
        } else {
            // 새 글 작성 모드이면 새 게시글 생성
            createNewPost(title, content, imageUrl)
        }
    }

    // Firestore에 게시글을 새로 생성하는 함수
    private fun createNewPost(title: String, content: String, imageUrl: String?) {
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
                "uid" to FirebaseAuth.getInstance().currentUser?.uid,  // << 꼭 포함!
                "nickname" to nickname,
                "hashtags" to hashtagList,
                "itemBuild" to selectedItems.mapNotNull { it?.name }, // ← 이 줄만 추가!
                "championId" to selectedChampionId  // ✅ 이 줄을 추가해야 돼!
            )
            postRef.set(postMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글 저장됨", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener {
            Toast.makeText(this, "닉네임 불러오기 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // Firestore에서 기존 게시글 문서를 업데이트하는 함수
    private fun updatePost(postId: String, title: String, content: String, imageUrl: String?) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uid = currentUser.uid
        db.collection("user_profiles").document(uid).get().addOnSuccessListener { document ->
            val nickname = document.getString("nickname") ?: "닉네임 없음"
            // 업데이트할 필드 구성 (필요한 필드를 추가하세요)
            val updatedData = hashMapOf(
                "title" to title,
                "content" to content,
                "category" to (editingTempPost?.category ?: currentCategory),
                "timestamp" to System.currentTimeMillis(),
                "imageUrl" to imageUrl,
                "uid" to FirebaseAuth.getInstance().currentUser?.uid,  // << 꼭 포함!
                "nickname" to nickname,
                "hashtags" to hashtagList,
                "itemBuild" to selectedItems.mapNotNull { it?.name }
                ,  // ✅ 아이템도 잊지 말고
                "championId" to selectedChampionId
            )
            db.collection("posts").document(postId)
                .update(updatedData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "게시글이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "게시글 수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "닉네임 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
