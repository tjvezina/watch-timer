# Wear OS Timer App Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone countdown timer app for Wear OS 3.5 with presets, background operation, full-screen alarm, and watch face complication.

**Architecture:** Single-activity Compose for Wear OS app with a foreground service for background countdown, AlarmManager for Doze-safe alarm delivery, a separate AlarmActivity for full-screen alarm, and DataStore for persistence. TimerService exposes a companion-object StateFlow observed by all UI and the complication.

**Tech Stack:** Kotlin 2.1.10, Jetpack Compose for Wear OS Material 3 (1.5.6), AGP 8.8.2, DataStore Preferences, Wear Complications Data Source KTX

**Spec:** `docs/superpowers/specs/2026-03-20-wear-os-timer-app-design.md`

**Prerequisites:** Android Studio (latest) installed with Wear OS SDK components. Gradle wrapper will be generated via `gradle wrapper`.

---

## File Structure

```
watch-timer/
├── settings.gradle.kts
├── build.gradle.kts                              # Project-level plugins
├── gradle.properties                              # JVM args, AndroidX flag
├── app/
│   ├── build.gradle.kts                           # App deps, SDK config
│   ├── proguard-rules.pro                         # Empty placeholder
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/watchtimerapp/
│       │   │   ├── MainActivity.kt                # Compose host, nav entry
│       │   │   ├── AlarmActivity.kt               # Full-screen alarm (separate activity)
│       │   │   ├── presentation/
│       │   │   │   ├── theme/Theme.kt             # Wear Material 3 theme
│       │   │   │   ├── screens/
│       │   │   │   │   ├── PresetListScreen.kt
│       │   │   │   │   ├── CountdownScreen.kt
│       │   │   │   │   ├── AlarmScreen.kt         # Compose content for AlarmActivity
│       │   │   │   │   ├── CustomPickerScreen.kt
│       │   │   │   │   └── SettingsScreen.kt
│       │   │   │   └── navigation/NavGraph.kt
│       │   │   ├── service/
│       │   │   │   └── TimerService.kt            # Foreground service + countdown
│       │   │   ├── receiver/
│       │   │   │   ├── AlarmReceiver.kt
│       │   │   │   └── BootReceiver.kt
│       │   │   ├── complication/
│       │   │   │   └── TimerComplicationService.kt
│       │   │   └── data/
│       │   │       ├── TimerState.kt              # Sealed class
│       │   │       ├── TimerRepository.kt         # Timer state persistence (boot survival)
│       │   │       ├── PresetRepository.kt        # Preset CRUD via DataStore
│       │   │       └── SettingsRepository.kt      # Sound/vibration prefs
│       │   └── res/
│       │       ├── drawable/ic_timer.xml           # App + complication icon
│       │       └── values/strings.xml
│       └── test/
│           └── java/com/watchtimerapp/
│               └── data/
│                   ├── TimerStateTest.kt
│                   ├── PresetRepositoryTest.kt
│                   └── SettingsRepositoryTest.kt
```

**Key design decisions:**
- `AlarmActivity` is a separate activity (not a nav destination) because full-screen intents launch activities, not Compose routes.
- `TimerService` uses a companion-object `StateFlow` so UI can observe without binding. State survives as long as the service process is alive; DataStore handles process death.
- `TimerRepository` is a thin persistence layer for boot survival — it stores the active timer's end time and pause state.

---

## Chunk 1: Project Setup + Data Layer

### Task 1: Create project scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_timer.xml`
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

```
*.iml
.gradle
/local.properties
/.idea
/build
/app/build
/captures
.externalNativeBuild
.cxx
local.properties
.superpowers/
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "watch-timer"
include(":app")
```

- [ ] **Step 3: Create project-level `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
}
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.watchtimerapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watchtimerapp"
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    // Wear Compose (not managed by BOM)
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha36")
    implementation("androidx.wear.compose:compose-foundation:1.5.0-beta01")
    implementation("androidx.wear.compose:compose-navigation:1.5.0-beta01")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.10.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Wear Complications
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Core + Lifecycle
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-service:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
```

- [ ] **Step 6: Create `app/proguard-rules.pro`**

```
# Empty — defaults are sufficient for this app
```

- [ ] **Step 7: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Timer</string>
    <string name="complication_label">Timer</string>
    <string name="timer_notification_channel">Timer</string>
    <string name="timer_running">Timer running</string>
</resources>
```

- [ ] **Step 8: Create `app/src/main/res/drawable/ic_timer.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z"/>
</vector>
```

- [ ] **Step 9: Generate Gradle wrapper and verify build**

Run:
```bash
cd /Users/tvezina/Projects/watch-timer
gradle wrapper --gradle-version 8.11.1
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (may download dependencies on first run).

Note: Requires `gradle` CLI installed (`brew install gradle`) to bootstrap the wrapper. After this step, only `./gradlew` is needed.

**Important:** Dependency versions in `app/build.gradle.kts` were researched as of March 2026. If the build fails with unresolvable dependency errors, check Maven Central / Google Maven for the latest stable versions and adjust accordingly. The key version groups that must stay compatible: Wear Compose (material3, foundation, navigation) should share the same version line; Compose BOM manages the `androidx.compose.ui:*` family; Kotlin plugin.compose version must match the Kotlin version exactly.

- [ ] **Step 10: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat app/
git commit -m "feat: scaffold Wear OS project with Gradle build files"
```

---

### Task 2: Create AndroidManifest and empty app shell

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/watchtimerapp/MainActivity.kt`
- Create: `app/src/main/java/com/watchtimerapp/presentation/theme/Theme.kt`

- [ ] **Step 1: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_timer"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault">

        <uses-library
            android:name="com.google.android.wearable"
            android:required="true" />

        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="true" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:taskAffinity=""
            android:theme="@android:style/Theme.DeviceDefault">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- AlarmActivity, TimerService, receivers, complication added in later tasks -->

    </application>
</manifest>
```

- [ ] **Step 2: Create `Theme.kt`**

```kotlin
package com.watchtimerapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun WatchTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
```

- [ ] **Step 3: Create `MainActivity.kt`**

```kotlin
package com.watchtimerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Text
import com.watchtimerapp.presentation.theme.WatchTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchTimerTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Timer App")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/
git commit -m "feat: add AndroidManifest, theme, and empty MainActivity"
```

---

### Task 3: TimerState sealed class

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/data/TimerState.kt`
- Create: `app/src/test/java/com/watchtimerapp/data/TimerStateTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class TimerStateTest {

    @Test
    fun `idle state has no remaining time`() {
        val state = TimerState.Idle
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `running state calculates remaining time from end time`() {
        val now = System.currentTimeMillis()
        val endTime = now + 60_000L
        val state = TimerState.Running(endTimeMillis = endTime, originalDurationMillis = 60_000L)
        val remaining = state.remainingMillis()
        assertTrue("Remaining should be ~60s, was $remaining", remaining in 59_000L..60_000L)
    }

    @Test
    fun `running state remaining never goes negative`() {
        val pastEndTime = System.currentTimeMillis() - 5_000L
        val state = TimerState.Running(endTimeMillis = pastEndTime, originalDurationMillis = 60_000L)
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `paused state returns stored remaining time`() {
        val state = TimerState.Paused(remainingMillis = 45_000L, originalDurationMillis = 60_000L)
        assertEquals(45_000L, state.remainingMillis())
    }

    @Test
    fun `alarming state has no remaining time`() {
        val state = TimerState.Alarming(originalDurationMillis = 60_000L)
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `formatRemainingTime formats minutes and seconds`() {
        assertEquals("5:00", TimerState.formatRemainingTime(300_000L))
        assertEquals("1:30", TimerState.formatRemainingTime(90_000L))
        assertEquals("0:05", TimerState.formatRemainingTime(5_000L))
        assertEquals("0:00", TimerState.formatRemainingTime(0L))
    }

    @Test
    fun `formatRemainingTime includes hours when over 60 minutes`() {
        assertEquals("1:00:00", TimerState.formatRemainingTime(3_600_000L))
        assertEquals("1:30:00", TimerState.formatRemainingTime(5_400_000L))
        assertEquals("2:05:30", TimerState.formatRemainingTime(7_530_000L))
    }

    @Test
    fun `formatApproxRemainingTime shows approximate values`() {
        assertEquals("5 min", TimerState.formatApproxRemainingTime(300_000L))
        assertEquals("2 min", TimerState.formatApproxRemainingTime(90_000L))
        assertEquals("<1 min", TimerState.formatApproxRemainingTime(30_000L))
        assertEquals("1 hr", TimerState.formatApproxRemainingTime(3_600_000L))
        assertEquals("1 hr 30 min", TimerState.formatApproxRemainingTime(5_400_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.watchtimerapp.data.TimerStateTest"`
Expected: FAIL — `TimerState` class does not exist yet.

- [ ] **Step 3: Implement `TimerState.kt`**

```kotlin
package com.watchtimerapp.data

sealed class TimerState {

    abstract fun remainingMillis(): Long

    object Idle : TimerState() {
        override fun remainingMillis(): Long = 0L
    }

    data class Running(
        val endTimeMillis: Long,
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long =
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    data class Paused(
        val remainingMillis: Long,
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long = remainingMillis
    }

    data class Alarming(
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long = 0L
    }

    companion object {
        fun formatRemainingTime(millis: Long): String {
            val totalSeconds = (millis + 999) / 1000 // round up
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

        fun formatApproxRemainingTime(millis: Long): String {
            val totalMinutes = (millis + 59_999) / 60_000 // round up
            return when {
                totalMinutes <= 0 -> "<1 min"
                totalMinutes < 60 -> "$totalMinutes min"
                else -> {
                    val hours = totalMinutes / 60
                    val mins = totalMinutes % 60
                    if (mins == 0L) "$hours hr"
                    else "$hours hr $mins min"
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.watchtimerapp.data.TimerStateTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/data/TimerState.kt app/src/test/java/com/watchtimerapp/data/TimerStateTest.kt
git commit -m "feat: add TimerState sealed class with formatting helpers"
```

---

### Task 4: PresetRepository

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/data/PresetRepository.kt`
- Create: `app/src/test/java/com/watchtimerapp/data/PresetRepositoryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class PresetRepositoryTest {

    @Test
    fun `default presets are 1, 3, 5, 10, 15, 30 minutes`() {
        val defaults = PresetRepository.DEFAULT_PRESETS
        assertEquals(
            listOf(60_000L, 180_000L, 300_000L, 600_000L, 900_000L, 1_800_000L),
            defaults
        )
    }

    @Test
    fun `formatPresetLabel formats minutes only`() {
        assertEquals("1 min", PresetRepository.formatPresetLabel(60_000L))
        assertEquals("5 min", PresetRepository.formatPresetLabel(300_000L))
        assertEquals("30 min", PresetRepository.formatPresetLabel(1_800_000L))
    }

    @Test
    fun `formatPresetLabel formats hours and minutes`() {
        assertEquals("1 hr", PresetRepository.formatPresetLabel(3_600_000L))
        assertEquals("1 hr 30 min", PresetRepository.formatPresetLabel(5_400_000L))
    }

    @Test
    fun `formatPresetLabel formats seconds when under a minute`() {
        assertEquals("30 sec", PresetRepository.formatPresetLabel(30_000L))
        assertEquals("45 sec", PresetRepository.formatPresetLabel(45_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.watchtimerapp.data.PresetRepositoryTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `PresetRepository.kt`**

```kotlin
package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

class PresetRepository(private val context: Context) {

    private val presetsKey = stringPreferencesKey("preset_list")

    val presets: Flow<List<Long>> = context.presetDataStore.data.map { prefs ->
        prefs[presetsKey]?.let { raw ->
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }
        } ?: DEFAULT_PRESETS
    }

    suspend fun savePresets(presets: List<Long>) {
        context.presetDataStore.edit { prefs ->
            prefs[presetsKey] = presets.joinToString(",")
        }
    }

    companion object {
        val DEFAULT_PRESETS: List<Long> = listOf(
            1 * 60_000L,
            3 * 60_000L,
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L,
        )

        fun formatPresetLabel(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 && minutes > 0 -> "$hours hr $minutes min"
                hours > 0 -> "$hours hr"
                minutes > 0 -> "$minutes min"
                else -> "$seconds sec"
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.watchtimerapp.data.PresetRepositoryTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/data/PresetRepository.kt app/src/test/java/com/watchtimerapp/data/PresetRepositoryTest.kt
git commit -m "feat: add PresetRepository with DataStore persistence and formatting"
```

---

### Task 5: SettingsRepository

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/data/SettingsRepository.kt`
- Create: `app/src/test/java/com/watchtimerapp/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `default sound enabled is true`() {
        assertTrue(SettingsRepository.DEFAULT_SOUND_ENABLED)
    }

    @Test
    fun `default vibration enabled is true`() {
        assertTrue(SettingsRepository.DEFAULT_VIBRATION_ENABLED)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.watchtimerapp.data.SettingsRepositoryTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `SettingsRepository.kt`**

```kotlin
package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")

    val soundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[soundKey] ?: DEFAULT_SOUND_ENABLED
    }

    val vibrationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[vibrationKey] ?: DEFAULT_VIBRATION_ENABLED
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[soundKey] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[vibrationKey] = enabled
        }
    }

    companion object {
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_VIBRATION_ENABLED = true
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.watchtimerapp.data.SettingsRepositoryTest"`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/data/SettingsRepository.kt app/src/test/java/com/watchtimerapp/data/SettingsRepositoryTest.kt
git commit -m "feat: add SettingsRepository for sound and vibration prefs"
```

---

### Task 6: TimerRepository (boot survival persistence)

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/data/TimerRepository.kt`

- [ ] **Step 1: Implement `TimerRepository.kt`**

```kotlin
package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_state")

class TimerRepository(private val context: Context) {

    private val endTimeKey = longPreferencesKey("end_time_millis")
    private val originalDurationKey = longPreferencesKey("original_duration_millis")
    private val isPausedKey = booleanPreferencesKey("is_paused")
    private val pausedRemainingKey = longPreferencesKey("paused_remaining_millis")

    suspend fun persistRunningTimer(endTimeMillis: Long, originalDurationMillis: Long) {
        context.timerDataStore.edit { prefs ->
            prefs[endTimeKey] = endTimeMillis
            prefs[originalDurationKey] = originalDurationMillis
            prefs[isPausedKey] = false
            prefs.remove(pausedRemainingKey)
        }
    }

    suspend fun persistPausedTimer(remainingMillis: Long, originalDurationMillis: Long) {
        context.timerDataStore.edit { prefs ->
            prefs.remove(endTimeKey)
            prefs[originalDurationKey] = originalDurationMillis
            prefs[isPausedKey] = true
            prefs[pausedRemainingKey] = remainingMillis
        }
    }

    suspend fun clearPersistedTimer() {
        context.timerDataStore.edit { it.clear() }
    }

    suspend fun loadPersistedTimer(): PersistedTimer? {
        val prefs = context.timerDataStore.data.first()
        val originalDuration = prefs[originalDurationKey] ?: return null
        val isPaused = prefs[isPausedKey] ?: false

        return if (isPaused) {
            val remaining = prefs[pausedRemainingKey] ?: return null
            PersistedTimer.Paused(remaining, originalDuration)
        } else {
            val endTime = prefs[endTimeKey] ?: return null
            PersistedTimer.Running(endTime, originalDuration)
        }
    }

    sealed class PersistedTimer {
        abstract val originalDurationMillis: Long

        data class Running(
            val endTimeMillis: Long,
            override val originalDurationMillis: Long,
        ) : PersistedTimer()

        data class Paused(
            val remainingMillis: Long,
            override val originalDurationMillis: Long,
        ) : PersistedTimer()
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/data/TimerRepository.kt
git commit -m "feat: add TimerRepository for persisting timer state across reboots"
```

---

## Chunk 2: Service Layer + Receivers

### Task 7: TimerService

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/service/TimerService.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add service declaration

- [ ] **Step 1: Implement `TimerService.kt`**

```kotlin
package com.watchtimerapp.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.watchtimerapp.MainActivity
import com.watchtimerapp.R
import com.watchtimerapp.data.TimerRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    private lateinit var timerRepository: TimerRepository

    override fun onCreate() {
        super.onCreate()
        timerRepository = TimerRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MILLIS, 0L)
                if (duration > 0) startTimer(duration)
            }
            ACTION_RESUME_WITH_END_TIME -> {
                val endTime = intent.getLongExtra(EXTRA_END_TIME_MILLIS, 0L)
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                if (endTime > 0) resumeFromEndTime(endTime, originalDuration)
            }
            ACTION_RESTORE_PAUSED -> {
                val remaining = intent.getLongExtra(EXTRA_REMAINING_MILLIS, 0L)
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                if (remaining > 0) restorePaused(remaining, originalDuration)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_CANCEL -> cancelTimer()
            ACTION_DISMISS_ALARM -> dismissAlarm()
            ACTION_FIRE_EXPIRED -> {
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                fireExpired(originalDuration)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startTimer(durationMillis: Long) {
        val endTime = System.currentTimeMillis() + durationMillis
        _timerState.value = TimerState.Running(
            endTimeMillis = endTime,
            originalDurationMillis = durationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))
        scheduleExactAlarm(endTime)
        scope.launch { timerRepository.persistRunningTimer(endTime, durationMillis) }
        startCountdown()
    }

    private fun resumeFromEndTime(endTimeMillis: Long, originalDurationMillis: Long) {
        _timerState.value = TimerState.Running(
            endTimeMillis = endTimeMillis,
            originalDurationMillis = originalDurationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        ))
        scheduleExactAlarm(endTimeMillis)
        startCountdown()
    }

    private fun restorePaused(remainingMillis: Long, originalDurationMillis: Long) {
        _timerState.value = TimerState.Paused(
            remainingMillis = remainingMillis,
            originalDurationMillis = originalDurationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    private fun pauseTimer() {
        val current = _timerState.value
        if (current is TimerState.Running) {
            val remaining = current.remainingMillis()
            _timerState.value = TimerState.Paused(
                remainingMillis = remaining,
                originalDurationMillis = current.originalDurationMillis,
            )
            countdownJob?.cancel()
            cancelExactAlarm()
            updateNotification(remaining)
            scope.launch {
                timerRepository.persistPausedTimer(remaining, current.originalDurationMillis)
            }
        }
    }

    private fun resumeTimer() {
        val current = _timerState.value
        if (current is TimerState.Paused) {
            val endTime = System.currentTimeMillis() + current.remainingMillis
            _timerState.value = TimerState.Running(
                endTimeMillis = endTime,
                originalDurationMillis = current.originalDurationMillis,
            )
            scheduleExactAlarm(endTime)
            scope.launch {
                timerRepository.persistRunningTimer(endTime, current.originalDurationMillis)
            }
            startCountdown()
        }
    }

    private fun cancelTimer() {
        countdownJob?.cancel()
        cancelExactAlarm()
        _timerState.value = TimerState.Idle
        scope.launch { timerRepository.clearPersistedTimer() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dismissAlarm() {
        _timerState.value = TimerState.Idle
        scope.launch { timerRepository.clearPersistedTimer() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fireExpired(originalDurationMillis: Long) {
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        startForeground(NOTIFICATION_ID, buildNotification(0L))
        AlarmReceiver.fireAlarm(this)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val state = _timerState.value
                if (state !is TimerState.Running) break
                val remaining = state.remainingMillis()
                if (remaining <= 0) {
                    onTimerFinished(state.originalDurationMillis)
                    break
                }
                updateNotification(remaining)
                delay(1_000L)
            }
        }
    }

    private fun onTimerFinished(originalDurationMillis: Long) {
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        cancelExactAlarm()
        AlarmReceiver.fireAlarm(this)
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            getAlarmPendingIntent(),
        )
    }

    private fun cancelExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getAlarmPendingIntent())
    }

    private fun getAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_EXPIRED
        }
        return PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(remainingMillis: Long): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.timer_running))
            .setContentText(TimerState.formatRemainingTime(remainingMillis))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(remainingMillis: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    companion object {
        const val ACTION_START = "com.watchtimerapp.action.START"
        const val ACTION_RESUME_WITH_END_TIME = "com.watchtimerapp.action.RESUME_WITH_END_TIME"
        const val ACTION_RESTORE_PAUSED = "com.watchtimerapp.action.RESTORE_PAUSED"
        const val ACTION_PAUSE = "com.watchtimerapp.action.PAUSE"
        const val ACTION_RESUME = "com.watchtimerapp.action.RESUME"
        const val ACTION_CANCEL = "com.watchtimerapp.action.CANCEL"
        const val ACTION_DISMISS_ALARM = "com.watchtimerapp.action.DISMISS_ALARM"
        const val ACTION_FIRE_EXPIRED = "com.watchtimerapp.action.FIRE_EXPIRED"

        const val EXTRA_DURATION_MILLIS = "duration_millis"
        const val EXTRA_END_TIME_MILLIS = "end_time_millis"
        const val EXTRA_ORIGINAL_DURATION_MILLIS = "original_duration_millis"
        const val EXTRA_REMAINING_MILLIS = "remaining_millis"

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun startTimer(context: Context, durationMillis: Long) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_MILLIS, durationMillis)
            }
            context.startForegroundService(intent)
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun cancelTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun dismissAlarm(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            context.startService(intent)
        }
    }
}
```

- [ ] **Step 2: Add service to `AndroidManifest.xml`**

Add inside the `<application>` tag, replacing the comment placeholder:

```xml
        <service
            android:name=".service.TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="timer" />
        </service>
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (AlarmReceiver referenced but not yet created — will fail. Create a stub first).

Note: If this fails due to missing `AlarmReceiver`, create a minimal stub:

```kotlin
// app/src/main/java/com/watchtimerapp/receiver/AlarmReceiver.kt
package com.watchtimerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stub — implemented in Task 8
    }

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.watchtimerapp.action.TIMER_EXPIRED"
        fun fireAlarm(context: Context) {
            // Stub — implemented in Task 8
        }
    }
}
```

Run again: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/service/TimerService.kt app/src/main/java/com/watchtimerapp/receiver/AlarmReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add TimerService with foreground notification and alarm scheduling"
```

---

### Task 8: AlarmReceiver + AlarmActivity

**Files:**
- Modify: `app/src/main/java/com/watchtimerapp/receiver/AlarmReceiver.kt`
- Create: `app/src/main/java/com/watchtimerapp/AlarmActivity.kt`
- Create: `app/src/main/java/com/watchtimerapp/presentation/screens/AlarmScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add receiver + AlarmActivity

- [ ] **Step 1: Implement full `AlarmReceiver.kt`**

Replace the stub with:

```kotlin
package com.watchtimerapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.watchtimerapp.AlarmActivity
import com.watchtimerapp.R
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_EXPIRED) {
            // If the service already transitioned to Alarming, it handled it.
            // This is the backup path for when Doze delayed the service.
            val currentState = TimerService.timerState.value
            if (currentState is TimerState.Running) {
                fireAlarm(context)
            }
        }
    }

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.watchtimerapp.action.TIMER_EXPIRED"
        private const val ALARM_CHANNEL_ID = "alarm_channel"
        private const val ALARM_NOTIFICATION_ID = 2

        fun fireAlarm(context: Context) {
            createAlarmChannel(context)

            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Time's Up!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(ALARM_NOTIFICATION_ID, notification)
        }

        private fun createAlarmChannel(context: Context) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Timer Alarm",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Timer alarm notifications"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 2: Implement `AlarmScreen.kt`**

```kotlin
package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

@Composable
fun AlarmScreen(
    originalDurationLabel: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Time's Up!",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = originalDurationLabel,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.size(width = 100.dp, height = 48.dp),
            shape = CircleShape,
        ) {
            Text("Dismiss", fontSize = 16.sp)
        }
    }
}
```

- [ ] **Step 3: Implement `AlarmActivity.kt`**

```kotlin
package com.watchtimerapp

import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.SettingsRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.screens.AlarmScreen
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on (lock screen + turn on handled via manifest attributes)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val currentState = TimerService.timerState.value
        val originalDuration = when (currentState) {
            is TimerState.Alarming -> currentState.originalDurationMillis
            is TimerState.Running -> currentState.originalDurationMillis
            else -> 0L
        }
        val durationLabel = PresetRepository.formatPresetLabel(originalDuration)

        startAlarmFeedback()

        setContent {
            WatchTimerTheme {
                AlarmScreen(
                    originalDurationLabel = durationLabel,
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    private fun startAlarmFeedback() {
        val settingsRepo = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            val soundEnabled = settingsRepo.soundEnabled.first()
            val vibrationEnabled = settingsRepo.vibrationEnabled.first()

            if (soundEnabled) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                    isLooping = true
                    play()
                }
            }

            if (vibrationEnabled) {
                vibrator = getSystemService(Vibrator::class.java)?.apply {
                    val pattern = longArrayOf(0, 500, 250, 500)
                    vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
            }
        }
    }

    private fun dismiss() {
        ringtone?.stop()
        vibrator?.cancel()
        TimerService.dismissAlarm(this)
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}
```

- [ ] **Step 4: Add AlarmActivity and AlarmReceiver to manifest**

Add inside `<application>`:

```xml
        <activity
            android:name=".AlarmActivity"
            android:exported="false"
            android:taskAffinity=""
            android:showOnLockScreen="true"
            android:turnScreenOn="true"
            android:theme="@android:style/Theme.DeviceDefault" />

        <receiver
            android:name=".receiver.AlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.watchtimerapp.action.TIMER_EXPIRED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/receiver/AlarmReceiver.kt app/src/main/java/com/watchtimerapp/AlarmActivity.kt app/src/main/java/com/watchtimerapp/presentation/screens/AlarmScreen.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add AlarmReceiver, AlarmActivity with sound/vibration, and AlarmScreen"
```

---

### Task 9: BootReceiver

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/receiver/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add receiver

- [ ] **Step 1: Implement `BootReceiver.kt`**

```kotlin
package com.watchtimerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.watchtimerapp.data.TimerRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerRepository = TimerRepository(context)
                val persisted = timerRepository.loadPersistedTimer() ?: return@launch

                when (persisted) {
                    is TimerRepository.PersistedTimer.Paused -> {
                        // Restore paused state
                        val serviceIntent = Intent(context, TimerService::class.java).apply {
                            action = TimerService.ACTION_RESTORE_PAUSED
                            putExtra(TimerService.EXTRA_REMAINING_MILLIS, persisted.remainingMillis)
                            putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                        }
                        context.startForegroundService(serviceIntent)
                    }
                    is TimerRepository.PersistedTimer.Running -> {
                        val now = System.currentTimeMillis()
                        if (persisted.endTimeMillis > now) {
                            // Timer still active — resume countdown
                            val serviceIntent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_RESUME_WITH_END_TIME
                                putExtra(TimerService.EXTRA_END_TIME_MILLIS, persisted.endTimeMillis)
                                putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                            }
                            context.startForegroundService(serviceIntent)
                        } else {
                            // Timer expired while device was off — start service in alarm state, then fire
                            val serviceIntent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_FIRE_EXPIRED
                                putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                            }
                            context.startForegroundService(serviceIntent)
                            timerRepository.clearPersistedTimer()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 2: Add BootReceiver to manifest**

Add inside `<application>`:

```xml
        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/receiver/BootReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add BootReceiver for timer restoration after reboot"
```

---

## Chunk 3: UI Screens + Navigation

### Task 10: Navigation graph

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt`

- [ ] **Step 1: Implement `NavGraph.kt`**

```kotlin
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
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt
git commit -m "feat: add navigation graph with route definitions"
```

---

### Task 11: PresetListScreen

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/presentation/screens/PresetListScreen.kt`
- Modify: `app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt` — wire in

- [ ] **Step 1: Implement `PresetListScreen.kt`**

```kotlin
package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.watchtimerapp.data.PresetRepository

@Composable
fun PresetListScreen(
    presetRepository: PresetRepository,
    onPresetSelected: (Long) -> Unit,
    onCustomSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val presets by presetRepository.presets.collectAsState(initial = PresetRepository.DEFAULT_PRESETS)
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text("Timer")
            }
        }

        items(presets) { durationMillis ->
            TitleCard(
                onClick = { onPresetSelected(durationMillis) },
                title = { Text(PresetRepository.formatPresetLabel(durationMillis)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }

        item {
            TitleCard(
                onClick = onCustomSelected,
                title = {
                    Text(
                        text = "+ Custom",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }

        item {
            TitleCard(
                onClick = onSettingsSelected,
                title = {
                    Text(
                        text = "Settings",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }
    }
}
```

- [ ] **Step 2: Wire into `NavGraph.kt`**

Replace the `composable(Routes.PRESET_LIST)` block:

```kotlin
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
```

Add imports at the top of NavGraph.kt:

```kotlin
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.watchtimerapp.presentation.screens.PresetListScreen
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.service.TimerService
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/presentation/screens/PresetListScreen.kt app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt
git commit -m "feat: add PresetListScreen with navigation wiring"
```

---

### Task 12: CountdownScreen

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/presentation/screens/CountdownScreen.kt`
- Modify: `app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt` — wire in

- [ ] **Step 1: Implement `CountdownScreen.kt`**

```kotlin
package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(
    onCancelled: () -> Unit,
) {
    val timerState by TimerService.timerState.collectAsState()

    // Tick every second to update the display
    var remainingMillis by remember { mutableLongStateOf(timerState.remainingMillis()) }
    LaunchedEffect(timerState) {
        while (true) {
            remainingMillis = timerState.remainingMillis()
            if (timerState is TimerState.Idle) {
                onCancelled()
                break
            }
            if (timerState is TimerState.Alarming) {
                // AlarmActivity handles the alarm via full-screen intent
                break
            }
            delay(100L)
        }
    }

    val progress = when (val state = timerState) {
        is TimerState.Running -> {
            val total = state.originalDurationMillis.toFloat()
            if (total > 0) remainingMillis / total else 0f
        }
        is TimerState.Paused -> {
            val total = state.originalDurationMillis.toFloat()
            if (total > 0) state.remainingMillis / total else 0f
        }
        else -> 0f
    }

    val isPaused = timerState is TimerState.Paused

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Circular progress around the edge
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = TimerState.formatRemainingTime(remainingMillis),
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                val context = androidx.compose.ui.platform.LocalContext.current

                Button(
                    onClick = {
                        if (isPaused) {
                            TimerService.resumeTimer(context)
                        } else {
                            TimerService.pauseTimer(context)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(if (isPaused) "▶" else "⏸", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { TimerService.cancelTimer(context) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text("✕", fontSize = 16.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Wire into `NavGraph.kt`**

Replace the `composable(Routes.COUNTDOWN)` block:

```kotlin
        composable(Routes.COUNTDOWN) {
            CountdownScreen(
                onCancelled = {
                    navController.popBackStack(Routes.PRESET_LIST, inclusive = false)
                },
            )
        }
```

Add import:

```kotlin
import com.watchtimerapp.presentation.screens.CountdownScreen
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/presentation/screens/CountdownScreen.kt app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt
git commit -m "feat: add CountdownScreen with progress indicator and pause/cancel"
```

---

### Task 13: CustomPickerScreen

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/presentation/screens/CustomPickerScreen.kt`
- Modify: `app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt` — wire in

- [ ] **Step 1: Implement `CustomPickerScreen.kt`**

```kotlin
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
```

- [ ] **Step 2: Wire into `NavGraph.kt`**

Replace the `composable(Routes.CUSTOM_PICKER)` block:

```kotlin
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
```

Add import:

```kotlin
import com.watchtimerapp.presentation.screens.CustomPickerScreen
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/presentation/screens/CustomPickerScreen.kt app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt
git commit -m "feat: add CustomPickerScreen with hour/minute/second pickers"
```

---

### Task 14: SettingsScreen

**Note:** Preset reorder is deferred to v1.1 — drag-to-reorder on a 1.4" round screen is complex UI work. v1 supports add and remove only. New presets are appended to the end of the list.

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/presentation/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt` — wire in

- [ ] **Step 1: Implement `SettingsScreen.kt`**

```kotlin
package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    presetRepository: PresetRepository,
    settingsRepository: SettingsRepository,
    onAddPreset: () -> Unit,
) {
    val presets by presetRepository.presets.collectAsState(initial = PresetRepository.DEFAULT_PRESETS)
    val soundEnabled by settingsRepository.soundEnabled.collectAsState(initial = true)
    val vibrationEnabled by settingsRepository.vibrationEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text("Settings")
            }
        }

        // Sound toggle
        item {
            SwitchButton(
                checked = soundEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setSoundEnabled(enabled) }
                },
                label = { Text("Sound") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }

        // Vibration toggle
        item {
            SwitchButton(
                checked = vibrationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setVibrationEnabled(enabled) }
                },
                label = { Text("Vibration") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }

        item {
            ListHeader {
                Text("Presets")
            }
        }

        // Existing presets with remove option
        items(presets) { durationMillis ->
            TitleCard(
                onClick = {
                    scope.launch {
                        val updated = presets.filter { it != durationMillis }
                        presetRepository.savePresets(updated)
                    }
                },
                title = { Text(PresetRepository.formatPresetLabel(durationMillis)) },
                subtitle = { Text("Tap to remove") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }

        // Add preset button
        item {
            TitleCard(
                onClick = onAddPreset,
                title = { Text("+ Add Preset") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }
    }
}
```

- [ ] **Step 2: Wire into `NavGraph.kt`**

Replace both the `composable(Routes.ADD_PRESET)` and `composable(Routes.SETTINGS)` placeholder blocks:

```kotlin
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
            val presetRepository = remember { PresetRepository(context) }
            val settingsRepository = remember { SettingsRepository(context) }
            SettingsScreen(
                presetRepository = presetRepository,
                settingsRepository = settingsRepository,
                onAddPreset = {
                    navController.navigate(Routes.ADD_PRESET)
                },
            )
        }
```

Add imports:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import com.watchtimerapp.presentation.screens.SettingsScreen
import com.watchtimerapp.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/presentation/screens/SettingsScreen.kt app/src/main/java/com/watchtimerapp/presentation/navigation/NavGraph.kt
git commit -m "feat: add SettingsScreen with sound/vibration toggles and preset management"
```

---

### Task 15: Wire up MainActivity with navigation

**Files:**
- Modify: `app/src/main/java/com/watchtimerapp/MainActivity.kt`

- [ ] **Step 1: Update `MainActivity.kt`**

Replace the entire file:

```kotlin
package com.watchtimerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.navigation.TimerNavGraph
import com.watchtimerapp.presentation.navigation.Routes
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.service.TimerService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startDestination = when (TimerService.timerState.value) {
            is TimerState.Running, is TimerState.Paused -> Routes.COUNTDOWN
            else -> Routes.PRESET_LIST
        }

        setContent {
            WatchTimerTheme {
                TimerNavGraph(startDestination = startDestination)
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/MainActivity.kt
git commit -m "feat: wire MainActivity to navigation graph with state-aware start destination"
```

---

## Chunk 4: Complication + Final Integration

### Task 16: TimerComplicationService

**Files:**
- Create: `app/src/main/java/com/watchtimerapp/complication/TimerComplicationService.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add complication service
- Modify: `app/src/main/java/com/watchtimerapp/service/TimerService.kt` — trigger complication updates

- [ ] **Step 1: Implement `TimerComplicationService.kt`**

```kotlin
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
```

- [ ] **Step 2: Add complication service to manifest**

Add inside `<application>`:

```xml
        <service
            android:name=".complication.TimerComplicationService"
            android:exported="true"
            android:icon="@drawable/ic_timer"
            android:label="@string/complication_label"
            android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER">
            <intent-filter>
                <action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST" />
            </intent-filter>
            <meta-data
                android:name="android.support.wearable.complications.SUPPORTED_TYPES"
                android:value="SHORT_TEXT" />
            <meta-data
                android:name="android.support.wearable.complications.UPDATE_PERIOD_SECONDS"
                android:value="0" />
        </service>
```

- [ ] **Step 3: Add complication update trigger to `TimerService.kt`**

Add import at the top of TimerService.kt:

```kotlin
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.watchtimerapp.complication.TimerComplicationService
```

Add a field in the class body after `private lateinit var timerRepository`:

```kotlin
    private var lastComplicationUpdateMinute = -1L
```

Add a method:

```kotlin
    private fun requestComplicationUpdate() {
        val requester = ComplicationDataSourceUpdateRequester.create(
            this,
            TimerComplicationService::class.java,
        )
        requester.requestUpdateAll()
    }
```

In the `startCountdown()` method, add complication update logic inside the while loop, after `updateNotification(remaining)`:

```kotlin
                // Update complication once per minute
                val currentMinute = remaining / 60_000
                if (currentMinute != lastComplicationUpdateMinute) {
                    lastComplicationUpdateMinute = currentMinute
                    requestComplicationUpdate()
                }
```

Also add `requestComplicationUpdate()` in the following three methods, just before `stopForeground`/`stopSelf` or state change:

In `cancelTimer()`, after `_timerState.value = TimerState.Idle`:
```kotlin
        requestComplicationUpdate()
```

In `dismissAlarm()`, after `_timerState.value = TimerState.Idle`:
```kotlin
        requestComplicationUpdate()
```

In `onTimerFinished()`, after `_timerState.value = TimerState.Alarming(...)`:
```kotlin
        requestComplicationUpdate()
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/watchtimerapp/complication/TimerComplicationService.kt app/src/main/java/com/watchtimerapp/service/TimerService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add TimerComplicationService with per-minute updates"
```

---

### Task 17: Final build verification

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run unit tests**

Run: `./gradlew test`
Expected: All tests PASS (TimerStateTest, PresetRepositoryTest, SettingsRepositoryTest).

---

### Task 18: Emulator testing checklist

This is a manual testing task. Deploy to the Wear OS emulator or TicWatch and verify each flow.

- [ ] **Step 1: Deploy to emulator**

Run: `./gradlew installDebug`
Or from Android Studio: select Wear OS emulator target, click Run.

- [ ] **Step 2: Verify preset list**

Open app → see list of presets (1 min, 3 min, 5 min, 10 min, 15 min, 30 min) + Custom + Settings.

- [ ] **Step 3: Verify timer start from preset**

Tap "1 min" → countdown screen shows "1:00", counts down, circular progress decreases.

- [ ] **Step 4: Verify pause/resume**

Tap pause → countdown stops. Tap resume → countdown continues from where it paused.

- [ ] **Step 5: Verify cancel**

Tap cancel → returns to preset list. No notification visible.

- [ ] **Step 6: Verify alarm fires**

Start "1 min" timer. Wait for countdown to reach 0. Verify:
- Screen wakes / takes over
- "Time's Up!" displayed with "1 min" label
- System alarm sound plays (if sound enabled)
- Watch vibrates (if vibration enabled)
- Tapping Dismiss stops sound/vibration and returns to preset list

- [ ] **Step 7: Verify background operation**

Start timer → press hardware button to go to watch face → wait for timer to expire. Verify alarm still fires.

- [ ] **Step 8: Verify custom picker**

Tap Custom → set 0:00:10 (10 seconds) → tap Start → verify 10-second countdown works.

- [ ] **Step 9: Verify settings**

Open Settings → toggle sound off → start timer → verify alarm fires with vibration only (no sound). Toggle vibration off → verify visual-only alarm.

- [ ] **Step 10: Verify reboot recovery (emulator only)**

Start a 5-minute timer. Run `adb reboot` from terminal. After emulator restarts, verify:
- Timer resumes countdown with approximately correct remaining time
- Or, if enough time passed during reboot, alarm fires immediately

Also test pause + reboot: pause a timer, reboot, verify it restores in paused state.

- [ ] **Step 11: Verify complication**

On watch face, add a complication → select "Timer". Start a timer → verify complication shows approximate remaining time (e.g., "3 min"). Tap complication → opens app.

- [ ] **Step 12: Verify add preset**

Open Settings → tap "+ Add Preset" → set 0:02:00 (2 minutes) → tap "Add". Verify "2 min" now appears in the preset list. Verify tapping it starts a timer (not another add).
