package io.github.ladogag.recentwikipediadeaths

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ladogag.recentwikipediadeaths.ui.FeedScreen
import io.github.ladogag.recentwikipediadeaths.ui.theme.RecentWikipediaDeathsTheme
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TrackerViewModel by lazy {
        (application as RecentWikipediaDeathsApp).trackerViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.refreshLocalization()
        setContent {
            RecentWikipediaDeathsTheme {
                FeedScreen(
                    viewModel = viewModel,
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}