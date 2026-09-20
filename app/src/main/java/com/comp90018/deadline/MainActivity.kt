package com.comp90018.deadline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.feature.debug.Person3TestScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            DeadlineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Person3TestScreen(
                        lifecycleOwner = this@MainActivity
                    )
                }
            }
        }
    }
}
