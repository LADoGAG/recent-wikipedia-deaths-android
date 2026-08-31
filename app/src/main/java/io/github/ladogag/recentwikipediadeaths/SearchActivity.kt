package io.github.ladogag.recentwikipediadeaths

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.ladogag.recentwikipediadeaths.ui.LanguageSearchScreen
import io.github.ladogag.recentwikipediadeaths.ui.theme.RecentWikipediaDeathsTheme
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel

class SearchActivity : ComponentActivity() {
    private val viewModel: TrackerViewModel by lazy {
        (application as RecentWikipediaDeathsApp).trackerViewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RecentWikipediaDeathsTheme {
                LanguageSearchScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}