# Breath Guide for Android

A local-only guided breathing timer with optional spoken cues using Android text-to-speech. Spoken cues can be turned on or off on the setup screen; they follow the inhale, exhale, retention, recovery hold, and release phases. The phone must have an English text-to-speech voice installed and its media volume audible. Set breaths per round, rounds, and inhale/exhale pace. After the final exhale, the retention timer runs until **Next — breathe in** is tapped. That tap saves the round's retention duration and begins a 10-second recovery hold. A 3-second exhale/rest follows. Completed round times are saved under the device's local session date. **Stop session** saves completed rounds; an unfinished retention is omitted.

## Open and run

Open this folder as a project in Android Studio with JDK 17 and Android SDK 36. Let Studio sync the Gradle project and install it on an Android 8.0+ device or emulator. The project specifies Android Gradle Plugin 8.13.2 and Gradle 8.13. The Gradle wrapper is included. Android Studio will download Gradle 8.13 when syncing; no standalone Gradle installation is needed.

The app uses platform Android APIs and has no runtime dependencies or network permissions. Text-to-speech uses the phone's installed speech engine and voice. Data is in app private preferences and will be removed if app data is cleared or the app is uninstalled. It does not run in the background: leaving the app stops and saves completed rounds. The device remains awake during a session.

Safety: practise seated or lying down, never in or near water or while driving. The timer is a record, not a goal. Breathe when needed. This is an independent guide and is not affiliated with Wim Hof Method.
