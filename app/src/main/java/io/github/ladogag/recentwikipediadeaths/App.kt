package io.github.ladogag.recentwikipediadeaths

import android.app.Application
import io.github.ladogag.recentwikipediadeaths.data.WikiRepository
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel

class RecentWikipediaDeathsApp : Application() {
    val trackerViewModel: TrackerViewModel by lazy { TrackerViewModel() }

    override fun onCreate() {
        super.onCreate()
        WikiRepository.init(this)
    }
}