package com.spendtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spendtracker.ui.SpendTrackerApp
import com.spendtracker.ui.theme.SpendTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpendTrackerTheme {
                SpendTrackerApp()
            }
        }
    }
}
