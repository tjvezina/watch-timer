package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState

private val TextColor = Color(0xFFCCCCCC)
private val DimTextColor = Color(0xFFAAAAAA)
private val SaveButtonColor = Color(0xFF27529C)
private val StartButtonColor = Color(0xFF274F27)

@Composable
fun CustomPickerScreen(
    onStartTimer: (Long) -> Unit,
    onSavePreset: (Long) -> Unit,
) {
    val hourState = rememberPickerState(initialNumberOfOptions = 24, initiallySelectedIndex = 0)
    val minuteState = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedIndex = 5)
    val secondState = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedIndex = 0)
    val isZero = hourState.selectedOptionIndex == 0 &&
        minuteState.selectedOptionIndex == 0 &&
        secondState.selectedOptionIndex == 0
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().height(80.dp),
        ) {
            Picker(
                state = hourState,
                contentDescription = { "Hours: %02d".format(hourState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
                gradientRatio = 0.5f,
            ) { index ->
                val isSelected = index == hourState.selectedOptionIndex
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) Color.White else DimTextColor,
                )
            }

            Text(":", fontSize = 24.sp, color = Color.White)

            Picker(
                state = minuteState,
                contentDescription = { "Minutes: %02d".format(minuteState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
                gradientRatio = 0.5f,
            ) { index ->
                val isSelected = index == minuteState.selectedOptionIndex
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) Color.White else DimTextColor,
                )
            }

            Text(":", fontSize = 24.sp, color = Color.White)

            Picker(
                state = secondState,
                contentDescription = { "Seconds: %02d".format(secondState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
                gradientRatio = 0.5f,
            ) { index ->
                val isSelected = index == secondState.selectedOptionIndex
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) Color.White else DimTextColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    val millis = (hourState.selectedOptionIndex * 3_600_000L) +
                        (minuteState.selectedOptionIndex * 60_000L) +
                        (secondState.selectedOptionIndex * 1_000L)
                    onSavePreset(millis)
                },
                enabled = !isZero,
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaveButtonColor),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Save",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    val millis = (hourState.selectedOptionIndex * 3_600_000L) +
                        (minuteState.selectedOptionIndex * 60_000L) +
                        (secondState.selectedOptionIndex * 1_000L)
                    onStartTimer(millis)
                },
                enabled = !isZero,
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StartButtonColor),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Start",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
