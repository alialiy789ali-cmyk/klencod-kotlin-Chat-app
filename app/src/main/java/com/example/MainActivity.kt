package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainHubScreen
import com.example.ui.SovereignViewModel
import com.example.ui.theme.SovereignTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appContext = LocalContext.current.applicationContext
            val sovereignViewModel: SovereignViewModel = viewModel(
                factory = SovereignViewModel.factory(appContext)
            )
            val userProfile by sovereignViewModel.currentUser.collectAsState()

            SovereignTheme(darkTheme = userProfile.isDarkMode) {
                MainHubScreen(
                    viewModel = sovereignViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}