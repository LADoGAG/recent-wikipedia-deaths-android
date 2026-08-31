package io.github.ladogag.recentwikipediadeaths

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.ladogag.recentwikipediadeaths.ui.SettingsScreen
import io.github.ladogag.recentwikipediadeaths.ui.theme.RecentWikipediaDeathsTheme
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel

class SettingsActivity : ComponentActivity() {
    private val viewModel: TrackerViewModel by lazy {
        (application as RecentWikipediaDeathsApp).trackerViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.refreshLocalization()
        setContent {
            RecentWikipediaDeathsTheme {
                val targets by viewModel.targets.collectAsState()
                if (targets.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SettingsScreen(viewModel, onBack = { finish() })
                }
            }
        }
    }
}