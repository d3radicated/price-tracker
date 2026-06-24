package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = PriceTrackerViewModel(application)

        setContent {
            val useDynamicColors by viewModel.useDynamicColors.collectAsState()
            MyApplicationTheme(dynamicColor = useDynamicColors) {
                val currentScreen by viewModel.currentScreen.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)

                    when (currentScreen) {
                        AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                        AppScreen.SCANNER -> ScannerScreen(viewModel)
                        AppScreen.REVIEW -> ReceiptReviewScreen(viewModel)
                        AppScreen.HISTORY -> ProductHistoryScreen(viewModel)
                        AppScreen.SETTINGS -> SettingsScreen(viewModel)
                    }
                }
            }
        }
    }
}
