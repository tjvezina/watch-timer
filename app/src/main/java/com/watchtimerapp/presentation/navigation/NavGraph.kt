package com.watchtimerapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.SettingsRepository
import com.watchtimerapp.presentation.screens.CountdownScreen
import com.watchtimerapp.presentation.screens.CustomPickerScreen
import com.watchtimerapp.presentation.screens.PresetListScreen
import com.watchtimerapp.presentation.screens.SettingsScreen
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
            val scope = rememberCoroutineScope()
            PresetListScreen(
                presetRepository = presetRepository,
                onPresetSelected = { duration ->
                    TimerService.startTimer(context, duration)
                    navController.navigate(Routes.COUNTDOWN) {
                        popUpTo(Routes.PRESET_LIST)
                    }
                },
                onPresetRemoved = { duration ->
                    scope.launch {
                        val current = presetRepository.presets.first()
                        presetRepository.savePresets(current.filter { it != duration })
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
            val context = LocalContext.current
            CustomPickerScreen(
                buttonLabel = "Start",
                onConfirm = { duration ->
                    TimerService.startTimer(context, duration)
                    navController.navigate(Routes.COUNTDOWN) {
                        popUpTo(Routes.PRESET_LIST)
                    }
                },
            )
        }
        composable(Routes.ADD_PRESET) {
            val context = LocalContext.current
            val presetRepository = remember { PresetRepository(context) }
            val scope = rememberCoroutineScope()
            CustomPickerScreen(
                buttonLabel = "Add",
                onConfirm = { duration ->
                    scope.launch {
                        val current = presetRepository.presets.first()
                        if (duration !in current) {
                            presetRepository.savePresets(current + duration)
                        }
                    }
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val settingsRepository = remember { SettingsRepository(context) }
            SettingsScreen(
                settingsRepository = settingsRepository,
            )
        }
    }
}
