# Watch Timer

Wear OS 3.5 timer app targeting TicWatch Pro 3 Ultra (WH12018).

## Build

- Requires `JAVA_HOME` pointed at Android Studio's bundled JDK:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Build: `./gradlew assembleDebug`
- Tests: `./gradlew test`
- Deploy to watch: `adb connect <watch-ip>:5555 && ./gradlew installDebug`
- Watch USB cable is power-only — all ADB is over Wi-Fi

## Design decisions

- Spec: `docs/superpowers/specs/2026-03-20-wear-os-timer-app-design.md`
- Plan: `docs/superpowers/plans/2026-03-20-wear-os-timer-app.md`
- Preset reorder deferred to v1.1 (too complex for 1.4" round screen)
- FSTN LCD layer (dual display) is not programmable — app renders on AMOLED only
- AlarmActivity is a separate activity (not a nav destination) because full-screen intents require it
- Verify builds from terminal (`./gradlew assembleDebug`) after code changes — cannot trigger Android Studio Run directly
