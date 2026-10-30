package com.shiftcam.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shiftcam.app.ui.BaseDataScreen
import com.shiftcam.app.ui.CameraScreen
import com.shiftcam.app.ui.CountScreen
import com.shiftcam.app.ui.OnboardingScreen
import com.shiftcam.app.ui.SettingsScreen
import com.shiftcam.app.ui.ShiftScreen
import com.shiftcam.app.ui.theme.ShiftCamTheme

enum class AppScreen {
    Onboarding,
    Shift,
    BaseData,
    Count,
    Camera,
    Settings,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiftCamTheme {
                val graph = ShiftCamApp.graphOf(this@MainActivity)
                AppRoot(graph)
            }
        }
    }
}

@Composable
private fun AppRoot(graph: AppGraph) {
    val setting by graph.settingRepository.observe().collectAsState(initial = null)
    var screen by remember { mutableStateOf<AppScreen?>(null) }

    LaunchedEffect(setting) {
        if (screen == null) {
            val s = setting
            if (s != null) {
                screen = if (s.onboardingDone) AppScreen.Shift else AppScreen.Onboarding
            }
        }
    }

    val current = screen
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    BackHandler(enabled = current != AppScreen.Shift && current != AppScreen.Onboarding) {
        screen = AppScreen.Shift
    }

    when (current) {
        AppScreen.Onboarding -> OnboardingScreen(graph) { screen = AppScreen.Shift }
        AppScreen.Shift -> ShiftScreen(graph) { screen = it }
        AppScreen.BaseData -> BaseDataScreen(graph) { screen = AppScreen.Shift }
        AppScreen.Count -> CountScreen(graph) { screen = AppScreen.Shift }
        AppScreen.Camera -> CameraScreen(graph) { screen = AppScreen.Shift }
        AppScreen.Settings -> SettingsScreen(graph) { screen = AppScreen.Shift }
    }
}
