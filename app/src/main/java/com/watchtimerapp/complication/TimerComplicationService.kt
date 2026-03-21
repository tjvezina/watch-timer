package com.watchtimerapp.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.watchtimerapp.MainActivity
import com.watchtimerapp.R
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService

class TimerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("5 min").build(),
            contentDescription = PlainComplicationText.Builder("Timer: 5 min remaining").build(),
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    Icon.createWithResource(this, R.drawable.ic_timer)
                ).build()
            )
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val state = TimerService.timerState.value

        return when (state) {
            is TimerState.Running -> {
                val approxText = TimerState.formatApproxRemainingTime(state.remainingMillis())
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(approxText).build(),
                    contentDescription = PlainComplicationText.Builder("Timer: $approxText remaining").build(),
                )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_timer)
                        ).build()
                    )
                    .setTapAction(tapIntent)
                    .build()
            }
            is TimerState.Paused -> {
                val approxText = TimerState.formatApproxRemainingTime(state.remainingMillis())
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("⏸ $approxText").build(),
                    contentDescription = PlainComplicationText.Builder("Timer paused: $approxText remaining").build(),
                )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_timer)
                        ).build()
                    )
                    .setTapAction(tapIntent)
                    .build()
            }
            else -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Timer").build(),
                    contentDescription = PlainComplicationText.Builder("Open Timer app").build(),
                )
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(this, R.drawable.ic_timer)
                        ).build()
                    )
                    .setTapAction(tapIntent)
                    .build()
            }
        }
    }
}
