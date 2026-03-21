package com.watchtimerapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.presentation.screens.CountdownScreen
import com.watchtimerapp.presentation.screens.PresetListScreen
import com.watchtimerapp.service.TimerService

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
            val context = LocalContext.current
            val presetRepository = remember { PresetRepository(context) }
            PresetListScreen(
                presetRepository = presetRepository,
                onPresetSelected = { duration ->
                    TimerService.startTimer(context, duration)
                    navController.navigate(Routes.COUNTDOWN) {
                        popUpTo(Routes.PRESET_LIST)
                    }
                },
                onCustomSelected = {
                    navController.navigate(Routes.CUSTOM_PICKER)
                },
                onSettingsSelected = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }
        composable(Routes.COUNTDOWN) {
            CountdownScreen(
                onCancelled = {
                    navController.popBackStack(Routes.PRESET_LIST, inclusive = false)
                },
            )
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
