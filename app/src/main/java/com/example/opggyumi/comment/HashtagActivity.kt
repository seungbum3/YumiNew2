package com.example.opggyumi.comment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.opggyumi.R

class HashtagActivity : AppCompatActivity() {
    private lateinit var hashtagEditText: EditText
    private lateinit var hashtagContainer: LinearLayout
    private lateinit var globalHashtagContainer: LinearLayout
    private val hashtags = mutableListOf<String>()
    private val globalHashtags = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hashtag)

        hashtagEditText = findViewById(R.id.hashtagEditText)
        hashtagContainer = findViewById(R.id.hashtagContainer)
        globalHashtagContainer = findViewById(R.id.globalHashtagContainer)

        // XML은 그대로 두고, 코드에서 globalHashtagContainer의 방향을 수직으로 변경
        globalHashtagContainer.orientation = LinearLayout.VERTICAL

        val backButton: ImageView = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

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

        loadGlobalHashtagsFromPrefs()

        // 입력할 때마다 전역 해시태그 제안 보여주기
        hashtagEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                showGlobalHashtagSuggestions(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // 해시태그 추가 함수 (개인 영역에 추가하고, 전역 해시태그 목록 업데이트)
    private fun addHashtag() {
        var tag = hashtagEditText.text.toString().trim()
        if (tag.isNotEmpty()) {
            if (!tag.startsWith("#")) {
                tag = "#$tag"
            }
            if (hashtags.size < 5) {
                hashtags.add(tag)
                // 개인 해시태그 영역에 추가할 컨테이너 (수평 LinearLayout)
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 8, 16, 8)
                }
                // 삭제 버튼 (x 아이콘)
                val deleteButton = ImageView(this).apply {
                    setImageResource(R.drawable.x_icon) // drawable 폴더에 x_icon 아이콘 필요
                    setPadding(8, 0, 0, 0)
                }
                // 아이콘 크기를 16dp로 설정
                val sizeInDp = 16
                val scale = resources.displayMetrics.density
                val sizeInPx = (sizeInDp * scale + 0.5f).toInt()
                deleteButton.layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx)

                // 해시태그 텍스트뷰: 글자 크기를 20sp로 설정
                val tagTextView = TextView(this).apply {
                    text = tag
                    textSize = 20f
                    setTextColor(Color.BLACK)
                }
                // 삭제 버튼 클릭 시 해당 해시태그 삭제
                deleteButton.setOnClickListener {
                    hashtags.remove(tag)
                    hashtagContainer.removeView(container)
                }
                // 삭제 버튼을 왼쪽에, 해시태그 텍스트를 오른쪽에 추가
                container.addView(deleteButton)
                container.addView(tagTextView)
                hashtagContainer.addView(container)
                hashtagEditText.text.clear()

                // 전역 해시태그 목록 업데이트 (없으면 추가)
                if (!globalHashtags.contains(tag)) {
                    globalHashtags.add(tag)
                    saveGlobalHashtagsToPrefs()
                }
            } else {
                Toast.makeText(this, "최대 5개까지 입력 가능합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SharedPreferences에서 전역 해시태그 목록 불러오기
    private fun loadGlobalHashtagsFromPrefs() {
        val prefs = getSharedPreferences("global_hashtags", MODE_PRIVATE)
        val saved = prefs.getStringSet("hashtags", emptySet()) ?: emptySet()
        globalHashtags.clear()
        globalHashtags.addAll(saved)
    }

    // SharedPreferences에 전역 해시태그 목록 저장
    private fun saveGlobalHashtagsToPrefs() {
        val prefs = getSharedPreferences("global_hashtags", MODE_PRIVATE)
        prefs.edit().putStringSet("hashtags", globalHashtags.toSet()).apply()
    }

    // 입력 텍스트에 따른 전역 해시태그 제안 표시 (각 제안은 왼쪽 정렬)
    private fun showGlobalHashtagSuggestions(query: String) {
        globalHashtagContainer.removeAllViews()
        if (query.isEmpty()) {
            return
        }
        val filtered = globalHashtags.filter { it.contains(query, ignoreCase = true) }
        for (tag in filtered) {
            val tagView = TextView(this).apply {
                text = tag
                textSize = 18f
                setTextColor(Color.DKGRAY)
                setPadding(12, 6, 12, 6)
                gravity = Gravity.START
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                // 클릭하면 EditText에 해당 해시태그를 입력함
                setOnClickListener {
                    hashtagEditText.setText(tag)
                    hashtagEditText.setSelection(tag.length)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 8)
                gravity = Gravity.START
            }
            tagView.layoutParams = params
            globalHashtagContainer.addView(tagView)
        }
    }
}
