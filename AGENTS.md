# Repository Guidelines

## Project Structure & Module Organization
- `app/`: Android app module.
- `app/src/main/java/com/example/uvccamerademo/`: Kotlin sources (Compose UI and camera logic).
- `app/src/main/res/`: resources (strings, icons, XML).
- `app/src/test/` and `app/src/androidTest/`: unit and instrumentation tests.
- `docs/`: project documentation (requirements/design).

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — build the debug APK.
- `./gradlew installDebug` — install to a connected device/emulator via adb.
- `./gradlew testDebugUnitTest` — run JVM unit tests.
- `./gradlew connectedAndroidTest` — run instrumentation tests (device/emulator required).

## Debugging & Device Logs
Build and debug (local):
1. `./gradlew assembleDebug` — compile the debug build.
2. Run from Android Studio (Run/Debug) to attach the debugger, or install via `./gradlew installDebug`.

Install to a real device and capture logs:
1. Pair/connect the device if needed: `adb pair <ip:port>` then `adb connect <ip:port>`.
2. Confirm the device: `adb devices`.
3. Install: `./gradlew installDebug`.
4. Clear logs: `adb logcat -c`.
5. Launch the app from the device.
6. Capture logs: `adb logcat -v time` (or `adb logcat -d -v time > logs.txt`).
7. Filter camera logs if needed: `adb logcat -v time | grep -i "uvc\|camera\|usb"`.

## Coding Style & Naming Conventions
- Kotlin/Compose with 4-space indentation; use Android Studio formatting.
- PascalCase for classes/files, camelCase for functions/variables.
- UI strings live in `app/src/main/res/values/strings.xml` and are referenced via `stringResource` or `context.getString`.
- Keep composables in `MainActivity.kt` / `PreviewScreens.kt` and scope side effects to lifecycle-aware blocks.

## Testing Guidelines
- Unit tests use JUnit in `app/src/test/...`.
- Instrumentation tests use AndroidX JUnit/Espresso in `app/src/androidTest/...`.
- Name tests by feature/class (e.g., `RecordingRepositoryTest`) and cover new behavior when feasible.

## Commit & Pull Request Guidelines
- Commit messages in this repo are short Japanese summaries; keep them concise and feature-focused.
- PRs should include a brief description, validation steps, and screenshots for UI changes. Link issues if available.

## Configuration & Tips
- `local.properties` should point to the Android SDK (`sdk.dir=...`); keep it local.
- If preview/adb issues occur, confirm device connection with `adb devices` and re-sync Gradle.
