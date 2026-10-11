package com.comp90018.deadline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as DeadlineApp).container.gameAudioManager.setAppForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            (application as DeadlineApp).container.gameAudioManager.setAppForeground(false)
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeadlineTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
