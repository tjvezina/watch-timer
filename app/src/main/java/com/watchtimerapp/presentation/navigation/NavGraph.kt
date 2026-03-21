package com.watchtimerapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

object Routes {
    const val PRESET_LIST = "preset_list"
    const val COUNTDOWN = "countdown"
    const val CUSTOM_PICKER = "custom_picker"
    const val ADD_PRESET = "add_preset"
    const val SETTINGS = "settings"
}

@Composable
fun TimerNavGraph(
    startDestination: String = Routes.PRESET_LIST,
) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.PRESET_LIST) {
            // PresetListScreen — wired in Task 11
        }
        composable(Routes.COUNTDOWN) {
            // CountdownScreen — wired in Task 12
        }
        composable(Routes.CUSTOM_PICKER) {
            // CustomPickerScreen — wired in Task 13
        }
        composable(Routes.ADD_PRESET) {
            // CustomPickerScreen (add preset mode) — wired in Task 14
        }
        composable(Routes.SETTINGS) {
            // SettingsScreen — wired in Task 14
        }
    }
}
