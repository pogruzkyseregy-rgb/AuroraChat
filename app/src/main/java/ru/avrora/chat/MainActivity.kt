package ru.avrora.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuroraNotifications.ensureChannel(this)
        setContent {
            MaterialTheme(colorScheme = AuroraColors) { ChatScreen() }
        }
    }

    override fun onStart() {
        super.onStart()
        // Забираем сообщения, которые Аврора написала в фоне, и убираем уведомление
        vm.drainInbox()
        vm.reloadDiary()
        AuroraNotifications.clear(this)
    }
}
