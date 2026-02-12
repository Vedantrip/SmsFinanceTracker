package com.example.smsfinancetracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smsfinancetracker.ui.dashboard.DashboardFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load DashboardFragment only once
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, DashboardFragment())
                .commit()
        }
    }
}
