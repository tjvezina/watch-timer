package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState

@Composable
fun CustomPickerScreen(
    buttonLabel: String = "Start",
    onConfirm: (Long) -> Unit,
) {
    val hourState = rememberPickerState(initialNumberOfOptions = 24, initiallySelectedIndex = 0)
    val minuteState = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedIndex = 5)
    val secondState = rememberPickerState(initialNumberOfOptions = 60, initiallySelectedIndex = 0)

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Picker(
                state = hourState,
                contentDescription = { "Hours: %02d".format(hourState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
            ) { index ->
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Text(":", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)

            Picker(
                state = minuteState,
                contentDescription = { "Minutes: %02d".format(minuteState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
            ) { index ->
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Text(":", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)

            Picker(
                state = secondState,
                contentDescription = { "Seconds: %02d".format(secondState.selectedOptionIndex) },
                modifier = Modifier.width(48.dp),
            ) { index ->
                Text(
                    text = "%02d".format(index),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val millis = (hourState.selectedOptionIndex * 3_600_000L) +
                    (minuteState.selectedOptionIndex * 60_000L) +
                    (secondState.selectedOptionIndex * 1_000L)
                if (millis > 0) {
                    onConfirm(millis)
                }
            },
        ) {
            Text(buttonLabel, fontSize = 16.sp)
        }
    }
}
