package com.phoneunison.mobile.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phoneunison.mobile.R

class MessagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)
        title = "Messages"
    }
}
