package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainApp
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkModePref by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val useDarkTheme = isDarkModePref ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = useDarkTheme) {
                MainApp(viewModel = viewModel)
            }
        }
    }
}
