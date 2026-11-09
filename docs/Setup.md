# Setup

## Prerequisites

- Android Studio with the Android SDK for compile SDK 37.1 (target SDK 36)
- JDK 17 or newer to run Gradle (9.5, via the wrapper) and AGP 9; Android Studio's bundled JDK works
- An Android phone with NFC running Android 11 (API 30) or newer, plus NTAG215 stickers. A second NFC phone running `:hce-emulator` can stand in for a sticker.

## Clone and build

```bash
git clone <repo-url>
cd "NFC Application Blocker"
./gradlew assembleDebug
```

Android Studio writes `local.properties` with your `sdk.dir` on first sync.

## Run

```bash
./gradlew :app:installDebug
```

Install with Gradle rather than Android Studio's Run button. Run force-stops the app before installing, and Android then turns off the app's accessibility service, so blocking silently stops until you switch it on again in Settings → Accessibility.

To install the tag emulator on a second phone:

```bash
./gradlew :hce-emulator:installDebug
```

## Debug helpers

Debug builds accept adb broadcasts, so you can test without a physical tag.

Simulate a tag tap. `content` is `blank`, `foreign`, `unreadable` or `ours:<uuid>`; leave it out to tap the existing virtual tag with that UID:

```bash
adb shell am broadcast -a nl.nulldevelopment.nfcappblocker.debug.TAP_TAG -p nl.nulldevelopment.nfcappblocker --es uid 04:00:00:00:00:00:01 --es content blank
```

Start onboarding over without clearing data:

```bash
adb shell am broadcast -a nl.nulldevelopment.nfcappblocker.debug.RESET_ONBOARDING -p nl.nulldevelopment.nfcappblocker
```
