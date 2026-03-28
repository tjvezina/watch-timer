# Watch Timer

Wear OS 3.5 timer app targeting TicWatch Pro 3 Ultra (WH12018).

## Build

- Requires `JAVA_HOME` pointed at Android Studio's bundled JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug`
- Tests: `./gradlew test`
- Deploy + launch: `./deploy.sh` (uninstalls first for clean channel state, grants SYSTEM_ALERT_WINDOW)
- Dump diagnostic logs: `./dump-logs.sh` (creates app + system log files)
- Watch USB cable is power-only — all ADB is over Wi-Fi

## Debugging

- Diagnostic logs are embedded in `TimerService`, `AlarmActivity`, `AlarmReceiver`, `BootReceiver` using Android `Log` with class-name TAGs
- `./dump-logs.sh` creates two files per run: `timer-*.log` (app TAGs) and `system-*.log` (system entries about FSI, notifications, ActivityTaskManager)
- Wear OS drops Wi-Fi in ambient mode, so ADB disconnects — reconnect after the fact and dump the buffer, or use Developer Options > Stay Awake while charging for live capture
- When diagnosing alarm/timer issues, always request device logs before guessing — these bugs are lifecycle/timing-dependent and not reproducible in code review alone
- The `deploy.sh` script uninstalls before installing to clear notification channels — channel importance is immutable after creation, so stale channels from prior builds can silently break FSI or notification behavior

## Alarm architecture — how AlarmActivity is launched

This section documents the activity launch mechanism. **Do not change this architecture without testing on device.** Check proposed changes against the constraints list below.

### The working mechanism (confirmed 2026-03-28: screen-on, backgrounded, process-dead, and ambient)

1. Countdown coroutine detects `remaining <= 0` → `onTimerFinished()` → state = Alarming, adds SYSTEM_ALERT_WINDOW overlay, starts vibration/sound. **Does NOT cancel the AlarmManager alarm.**
2. ~1s later, AlarmManager fires → `AlarmReceiver.onReceive()` adds its own overlay (in case onTimerFinished didn't run first), then calls `startForegroundService(ACTION_FIRE_EXPIRED)`.
3. `fireExpired()` posts the FSI notification (ID=2) with `setFullScreenIntent(AlarmActivity)`.
4. The SYSTEM_ALERT_WINDOW overlay makes the FSI notification launch AlarmActivity (~1s delay). Without the overlay, FSI only shows a heads-up on this device.
5. If user is in the foreground when the countdown fires, `CountdownScreen` transitions to `AlarmScreen` inline — the AlarmManager still fires and launches AlarmActivity on top (harmless duplicate).
6. The overlay is removed by `AlarmActivity.onCreate()` as soon as the activity is visible (minimizes "displaying over other apps" system notification).

### Why the SYSTEM_ALERT_WINDOW overlay is needed

- Direct `startActivity()` from a BroadcastReceiver is blocked — the system attributes it to the foreground service process, not the receiver's PendingIntent exemption (`isBgStartWhitelisted: false`).
- Direct `PendingIntent.getActivity()` from AlarmManager is also blocked (`Abort background activity starts`).
- FSI alone doesn't launch activities on this device — the dual-display FSTN LCD makes the system treat the screen as "on" even in ambient mode, so FSI shows heads-up instead.
- SYSTEM_ALERT_WINDOW + a TYPE_APPLICATION_OVERLAY window makes FSI actually launch the activity. The permission must be granted via `adb shell appops set com.watchtimerapp SYSTEM_ALERT_WINDOW allow` (handled by `deploy.sh`).

### Constraints — verified on device 2026-03-28

These have all been tried and failed on this device. Check proposed changes against this list.

0. **Do NOT share PendingIntents between the foreground service notification and the FSI notification** — If the same `PendingIntent.getActivity(AlarmActivity, requestCode=0)` is used for both, the system treats it as consumed and the FSI fails. The foreground service notification must point to `MainActivity`.
1. **Do NOT rely on FSI alone** — FSI only launches activities when combined with SYSTEM_ALERT_WINDOW + overlay. Without the overlay, FSI shows heads-up instead of launching AlarmActivity (dual-display FSTN treats screen as "on").
2. **Do NOT use `PendingIntent.getActivity()` as the AlarmManager operation intent** — System explicitly blocks it: `Abort background activity starts`. Tested 2026-03-28.
3. **Do NOT call `startActivity()` from a BroadcastReceiver or Service without the overlay** — Even from AlarmReceiver.onReceive(), the system attributes the call to the foreground service process. `isBgStartWhitelisted: false`, activity start "stopped". Tested 2026-03-28.
4. **Do NOT call `cancelExactAlarm()` from `onTimerFinished()`** — The countdown coroutine often beats the AlarmManager. Cancelling the alarm prevents the receiver from running. Alarm cancellation belongs in `dismissAlarm()`, `cancelTimer()`, and `pauseTimer()` only.
5. **Do NOT use IMPORTANCE_HIGH for the foreground service notification** — It pops up as a heads-up and steals focus from AlarmActivity.
6. **Do NOT check in-memory `TimerState` as a gate for forwarding AlarmManager events to the service** — The static state resets to Idle on process death. The receiver must always start the service unconditionally.
7. **Do NOT put FSI on the foreground service notification via `startForeground()`** — FSI posted this way does not reliably launch activities on Wear OS.

## Design decisions

- Spec: `docs/superpowers/specs/2026-03-20-wear-os-timer-app-design.md`
- Plan: `docs/superpowers/plans/2026-03-20-wear-os-timer-app.md`
- Preset reorder deferred to v1.1 (too complex for 1.4" round screen)
- FSTN LCD layer (dual display) is not programmable — app renders on AMOLED only
- AlarmActivity is a separate activity (not a nav destination) because it must be launchable from the receiver via `startActivity()`
- `setExactAndAllowWhileIdle()` is used instead of `setAlarmClock()` because `setAlarmClock()` may wake the screen
- The foreground service notification (ID=1) is IMPORTANCE_MIN / hidden, points to MainActivity. The FSI alarm notification (ID=2) is IMPORTANCE_HIGH on a separate `alarm_channel`, points to AlarmActivity. These must remain separate and use different PendingIntents.
- Alarm auto-timeout is 10 minutes — user sometimes leaves alarms ringing intentionally while busy
- Stale alarm threshold is 5 minutes — only applies to reboot/process-restart recovery, not to a normally-running alarm

## Known issues

### Resolved (2026-03-28)

1. **Vibration stopping early** — No PARTIAL_WAKE_LOCK; AlarmActivity.onDestroy auto-dismissed on system kill (missing `isFinishing` check); START_STICKY null intent left service idle after process death.
2. **Stale alarm firing hours late** — Persisted timer never cleared on alarm entry; `stopSelf()` raced ahead of `clearPersistedTimer()`.
3. **AlarmReceiver dropping alarms** — Gated on in-memory state that resets on process death. (See constraint #6.)
4. **MainActivity startDestination wrong for Alarming state** — Fell through to PRESET_LIST.
5. **cancelExactAlarm race condition** — Countdown coroutine beats AlarmManager by ~1s; cancelling the alarm killed the receiver's exemption. (See constraint #4.)
6. **FSI PendingIntent collision** — Foreground service and FSI notification shared the same PendingIntent. (See constraint #0.)
7. **AlarmActivity not launching from background** — Direct startActivity from receiver and PendingIntent.getActivity from AlarmManager both blocked. Fixed with SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY window, which makes FSI launch the activity.

### Open / monitoring

8. **Vibration stopped on screen touch (observed once, not reproduced)** — PARTIAL_WAKE_LOCK was held. Request device logs if it recurs.
9. **Timer accuracy in Doze** — If late timers are reported, consider switching back to `setAlarmClock()`.
10. **Activity launch ~1s delay** — AlarmActivity takes ~1s to appear after the alarm fires. The direct startActivity is blocked; the FSI notification launches it with a short delay. Acceptable for now.
