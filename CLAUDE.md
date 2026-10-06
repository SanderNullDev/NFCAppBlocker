# NFC App Blocker

Personal Android app: scanning a self-configured NFC tag toggles blocking of a chosen set of apps. Not planned for the Play Store, but the design shouldn't rule it out.

Read before working:

- `docs/IMPLEMENTATION_PLAN.md`: scope, phases, testing strategy, open questions
- `docs/DECISIONS.md`: decision log. Never edit an accepted decision; add a new entry that supersedes it.

## Git rules

- Personal project. Commit only with the repo-local identity (Sander van 't Veer <sander.vantveer@gmail.com>). Never change the local git identity and never commit with the global identity.
- Commit signing is intentionally disabled in this repo. Don't re-enable it or pass `-S`.
- Ask before committing or pushing.

## How to work

- Verify the project structure at the start of a session.
- Before implementing, present the options with trade-offs and wait for a choice.
- Show the intended changes and get explicit approval before modifying files. Don't change files while still comparing alternatives.
- Ask when something is unclear rather than guessing.
- Record significant new decisions in `docs/DECISIONS.md` and keep `docs/IMPLEMENTATION_PLAN.md` up to date.

## Stack

- Kotlin, Jetpack Compose, Material 3. AndroidX only.
- ViewModel + StateFlow, coroutines and Flow. Hilt once DI is introduced in `:app`.
- Accessibility first: TalkBack labels, touch targets of at least 48dp, state never conveyed by color alone.
- Tests: JUnit 5 (Jupiter) for local unit tests via `useJUnitPlatform()`, without the android-junit5 plugin. MockK and Turbine when needed. Instrumented tests stay on the AndroidX JUnit 4 runner.

## Modules

| Module | What it is |
|---|---|
| `:app` | The blocker app, `nl.nulldevelopment.nfcappblocker`, minSdk 30 |
| `:core:tag-format` | Pure Kotlin/JVM: tag MIME type, payload format and NDEF encoding/decoding. Shared by `:app` and `:hce-emulator`, so the format is defined in one place. No Android dependencies. |
| `:hce-emulator` | Test tool for a second phone: emulates an NFC Forum Type 4 tag via HCE, serving our NDEF message. `nl.nulldevelopment.nfcappblocker.hceemulator`, minSdk 30 |

## Commands

```bash
./gradlew :core:tag-format:test
./gradlew :hce-emulator:testDebugUnitTest
./gradlew :hce-emulator:installDebug
./gradlew :hce-emulator:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
./gradlew :app:lintDebug
```

Install `:app` with `./gradlew :app:installDebug`, not Android Studio's Run button. Run force-stops the app before installing, and Android then turns off the app's accessibility service, so blocking silently stops until it's re-enabled in Settings → Accessibility.
