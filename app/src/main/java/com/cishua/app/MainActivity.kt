package com.cishua.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import com.cishua.app.ui.AppRoot
import com.cishua.app.ui.Speaker
import com.cishua.app.vm.FeedViewModel

class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker
    private lateinit var feedViewModel: FeedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        speaker = Speaker(applicationContext)
        feedViewModel = FeedViewModel(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { feedViewModel.attach(scope) }
            AppRoot(feedViewModel, speaker)
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
