package com.example.yumi2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainpageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mainpage)



        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.selectedItemId = R.id.category1  // '홈'을 기본 선택

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.category1 -> true  // 현재 페이지이므로 이동하지 않음
                R.id.category2 -> {
                    finish()
                    true
                }
                R.id.category3 -> {
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

}
