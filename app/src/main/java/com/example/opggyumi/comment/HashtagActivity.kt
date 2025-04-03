package com.example.opggyumi.comment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.opggyumi.R

class HashtagActivity : AppCompatActivity() {
    private lateinit var hashtagEditText: EditText
    private lateinit var hashtagContainer: LinearLayout
    private val hashtags = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hashtag)

        hashtagEditText = findViewById(R.id.hashtagEditText)
        hashtagContainer = findViewById(R.id.hashtagContainer)

        // 뒤로가기 버튼
        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
        // 키보드 엔터(IME 액션 Done) 이벤트 처리
        hashtagEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addHashtag()
                true
            } else {
                false
            }
        }
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putStringArrayListExtra("hashtags", ArrayList(hashtags))
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
    // 해시태그 추가 함수
    private fun addHashtag() {
        val tag = hashtagEditText.text.toString().trim()
        if (tag.isNotEmpty()) {
            if (hashtags.size < 5) {
                hashtags.add(tag)
                val tagView = TextView(this)
                tagView.text = tag
                tagView.textSize = 16f
                hashtagContainer.addView(tagView)
                hashtagEditText.text.clear()
            } else {
                Toast.makeText(this, "최대 5개까지 입력 가능합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
