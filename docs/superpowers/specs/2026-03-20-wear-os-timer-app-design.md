# Wear OS Timer App — Design Spec

## Overview

A standalone countdown timer app for Wear OS 3.5 (TicWatch Pro 3 Ultra, model WH12018). The user selects a preset or custom duration, the timer runs in the background, and fires an alarm (sound + vibration) that takes over the screen when time is up. The alarm rings until explicitly dismissed.

**Target device:** TicWatch Pro 3 Ultra (Snapdragon Wear 4100+, 1 GB RAM, 1.4" round AMOLED 454x454)
**Platform:** Wear OS 3.5 (API 30)
**Stack:** Kotlin, Jetpack Compose for Wear OS (Material 3), Foreground Service, AlarmManager, DataStore

## Functional Requirements

### Timer Behavior

- Single countdown timer (one at a time)
- User selects a duration from presets or sets a custom duration via scroll picker
- Timer runs in the background via a foreground service
- Timer survives the user navigating away from the app (back to watch face, other apps)
- Timer survives device reboot: the target end time (and pause state, if paused) is persisted to DataStore, and on boot a receiver either resumes the countdown, restores the paused state, or fires the alarm immediately if the end time has already passed
- Pause/resume and cancel controls available during countdown

### Alarm Behavior

- When the timer reaches zero, a full-screen intent takes over the screen (wakes screen if off, shows over lock screen)
- Plays the system default alarm sound on loop until dismissed (unless silent mode is enabled)
- Vibrates on loop until dismissed (unless vibration is disabled)
- Single "Dismiss" button stops the alarm, clears the notification, and returns to the preset list

### Presets

- Editable list of timer presets with default set: 1, 3, 5, 10, 15, 30 minutes
- User can add, remove, and reorder presets via a settings screen
- Presets are persisted locally via DataStore
- A "Custom" option at the bottom of the preset list opens the duration picker

### Custom Duration Picker

- Scrollable wheels for hours, minutes, and seconds
- Supports rotary crown input for quick scrolling
- Confirm button starts the timer immediately

### Settings

- **Edit presets:** add, remove, reorder saved presets
- **Sound toggle:** on (system default alarm) / off (silent — vibrate only)
- **Vibration toggle:** on / off
- If both sound and vibration are off, the alarm is visual-only (screen takeover, dismiss button, no audio or haptic)
- Settings persisted via DataStore

### Complication

- Provides a `SHORT_TEXT` complication for watch faces
- When a timer is running: shows remaining time in approximate format (e.g., "3 min") to reflect that it updates once per minute, not per second
- When no timer is running: shows the app icon as a launch shortcut
- Tapping the complication opens the app (countdown screen if running, preset list if idle)
- Updated once per minute via `ComplicationDataSourceUpdateRequester`, triggered by the foreground service

## Architecture

### Components

#### `TimerService` (Foreground Service)

- Starts when a timer begins, stops when alarm is dismissed or timer is cancelled
- Maintains a `StateFlow<TimerState>` shared with the UI and complication
- Runs the countdown via coroutine, ticking every second
- Shows a persistent notification with remaining time (Android requirement for foreground services)
- Schedules an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` as a Doze-safe backup
- Triggers complication update once per minute

#### `AlarmReceiver` (BroadcastReceiver)

- Receives the exact alarm broadcast when the timer expires
- Launches the alarm screen via full-screen intent (wakes screen, shows over lock screen)

#### `BootReceiver` (BroadcastReceiver)

- Listens for `BOOT_COMPLETED`
- Reads persisted timer end time from DataStore
- If timer was paused: restores `TimerService` in paused state with remaining duration
- If end time is in the future: restarts `TimerService` with remaining duration
- If end time is in the past: fires alarm immediately (timer expired while device was off)
- If no persisted timer: does nothing

#### `TimerComplicationService`

- Extends `SuspendingComplicationDataSourceService`
- Reads `TimerState` to provide current data
- Returns `SHORT_TEXT` with approximate remaining time ("3 min") or app icon when idle

#### `TimerState` (Shared State)

- Sealed class: `Idle`, `Running(endTimeMillis, pausedRemainingMillis?)`, `Paused(remainingMillis)`, `Alarming`
- Exposed as a `StateFlow` from `TimerService`
- Observed by screens and complication

#### `PresetRepository`

- CRUD operations for timer presets
- Backed by Jetpack DataStore (Preferences)
- Default presets: 1, 3, 5, 10, 15, 30 minutes

#### `SettingsRepository`

- Sound enabled (Boolean, default: true)
- Vibration enabled (Boolean, default: true)
- Backed by Jetpack DataStore (Preferences)

### Data Flow

```
User taps preset
  → MainActivity starts TimerService with duration
  → TimerService:
      1. Persists target end time to DataStore
      2. Schedules exact alarm via AlarmManager
      3. Starts foreground notification
      4. Begins countdown coroutine, emitting TimerState.Running
  → UI observes TimerState via StateFlow, shows CountdownScreen
  → Every 60s: TimerService triggers complication update
  → Timer reaches zero OR AlarmReceiver fires:
      1. TimerState → Alarming
      2. Full-screen intent launches AlarmScreen
      3. Sound + vibration begin
  → User taps Dismiss:
      1. Sound + vibration stop
      2. TimerState → Idle
      3. Persisted end time cleared from DataStore
      4. Foreground service stops
      5. Alarm cancelled
      6. Navigate to PresetListScreen
```

## Screens

### 1. Preset List (Home)

- `ScalingLazyColumn` of saved presets
- Each item shows the duration label (e.g., "5 min", "1 hr 30 min")
- Tap to immediately start that timer
- "Custom" item at the bottom navigates to the picker
- Settings gear icon navigates to settings
- If a timer is currently running, navigates directly to CountdownScreen on launch

### 2. Countdown

- Large centered countdown text (MM:SS or H:MM:SS)
- Circular progress indicator around the edge of the round screen
- Pause/Resume button
- Cancel button (returns to preset list, stops service)
- Timer continues in background if user navigates away

### 3. Alarm (Full-Screen Intent)

- Launched via full-screen intent — takes over even from watch face or lock screen
- "Time's Up!" message with the original preset duration shown
- Dismiss button — large, easy to tap
- Screen stays on while alarming (wake lock)
- Sound + vibration loop until dismissed

### 4. Custom Picker

- Three scrollable columns: Hours, Minutes, Seconds
- Rotary crown scrolls the focused column
- Start button begins the timer
- Back navigation returns to preset list

### 5. Settings

- Edit presets: list with add/remove/reorder
- Sound toggle: on/off
- Vibration toggle: on/off

## Permissions

| Permission | Purpose | Grant type |
|---|---|---|
| `FOREGROUND_SERVICE` | Keep timer alive in background | Auto-granted |
| `VIBRATE` | Haptic alarm feedback | Auto-granted |
| `USE_FULL_SCREEN_INTENT` | Take over screen for alarm | Auto-granted |
| `WAKE_LOCK` | Wake + keep screen on for alarm | Auto-granted |
| `RECEIVE_BOOT_COMPLETED` | Restore timer after reboot | Auto-granted |
| `SCHEDULE_EXACT_ALARM` | Required for exact alarms on API 31+ (targetSdk 33) | Auto-granted on Wear OS |

No dangerous permissions. No runtime permission dialogs.

## Project Structure

```
watch-timer/
├── app/
│   └── src/main/
│       ├── java/com/watchtimerapp/
│       │   ├── MainActivity.kt
│       │   ├── presentation/
│       │   │   ├── theme/Theme.kt
│       │   │   ├── screens/
│       │   │   │   ├── PresetListScreen.kt
│       │   │   │   ├── CountdownScreen.kt
│       │   │   │   ├── AlarmScreen.kt
│       │   │   │   ├── CustomPickerScreen.kt
│       │   │   │   └── SettingsScreen.kt
│       │   │   └── navigation/NavGraph.kt
│       │   ├── service/
│       │   │   └── TimerService.kt
│       │   ├── receiver/
│       │   │   ├── AlarmReceiver.kt
│       │   │   └── BootReceiver.kt
│       │   ├── complication/
│       │   │   └── TimerComplicationService.kt
│       │   └── data/
│       │       ├── TimerState.kt
│       │       ├── PresetRepository.kt
│       │       └── SettingsRepository.kt
│       ├── res/
│       │   ├── drawable/
│       │   └── values/strings.xml
│       └── AndroidManifest.xml
├── build.gradle.kts
└── app/build.gradle.kts
```

## Out of Scope (v1)

- Multiple simultaneous timers
- Interval/repeating timers
- Tiles API (swipeable card view)
- `RANGED_VALUE` complication type (progress arc)
- Custom alarm sounds
- Network features
- Phone companion app
- Preset reorder (deferred to v1.1 — complex on tiny round screen)
- FSTN LCD layer (not programmable by third-party apps)

## Target Configuration

- `minSdk = 30` (Wear OS 3.5 / Android 11)
- `targetSdk = 33` (recommended for forward compatibility)
- Single-module Gradle project
- Kotlin + Jetpack Compose for Wear OS (Material 3)
