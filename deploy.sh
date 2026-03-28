#!/bin/bash
# Builds debug APK and installs it on the TicWatch.

WATCH_IP="192.168.0.80"

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

adb connect "${WATCH_IP}:5555"
adb uninstall com.watchtimerapp 2>/dev/null
./gradlew installDebug \
  && adb shell appops set com.watchtimerapp SYSTEM_ALERT_WINDOW allow \
  && adb shell am start -n com.watchtimerapp/.MainActivity
