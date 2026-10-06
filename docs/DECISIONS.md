# Decision Log

One entry per decision: what was decided, why, and what was considered instead. Add new entries at the bottom; supersede old ones rather than editing them.

Status values: **Accepted**, **Superseded by D-xxx**, **Deferred**.

---

## D-001 — Enforce blocking with an AccessibilityService

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** Detect blocked apps coming to the foreground via an AccessibilityService (`TYPE_WINDOW_STATE_CHANGED`) and redirect to a block screen.
- **Why:** Reacts immediately and is event-driven, so no polling battery cost.
- **Alternatives:** UsageStatsManager polling + overlay — rejected because of battery drain, slower reaction, and background activity start restrictions on newer Android versions.
- **Consequences:** Play Store requires an AccessibilityService declaration and justification if the app is ever published. OEMs may kill the service; the app must detect and surface this.

## D-002 — Minimum SDK 26

- **Date:** 2026-10-02
- **Status:** Superseded by D-010
- **Decision:** `minSdk = 26` (Android 8.0).
- **Why:** Covers all test devices and avoids older NFC and background-execution quirks.

## D-003 — Many tags can control one profile

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** A profile can have multiple linked tags; each tag belongs to exactly one profile.
- **Why:** Allows e.g. one tag downstairs and one next to the bed for the same profile.

## D-004 — One active profile at a time (multiple later)

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** Only one profile can be active at a time for now.
- **Why:** Simpler mental model and UI for the MVP.
- **Consequences:** Model the active state as a collection anyway, so supporting multiple active profiles later doesn't require a data migration.

## D-005 — Scanning toggles blocking

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** Scanning a paired tag toggles its profile on or off.
- **Alternatives:** "Scan to unlock for X minutes" — deferred as a separate, additional mechanism.

## D-006 — Escape hatch is a configurable countdown

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** Without a tag, blocking can be stopped by completing a countdown. Default 3 minutes, configurable from 1 to 20 minutes.
- **Why:** Adds friction without making a lost tag a lockout.
- **Alternatives:** Typing a long phrase, a backup recovery code generated at setup.

## D-007 — No tamper resistance

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** The app does not prevent uninstalling itself or disabling the AccessibilityService while blocking is active.
- **Why:** Personal use; bypassing the block that way is a deliberate choice by the user, and tamper resistance is easy to make hostile.

## D-008 — Tags are identified by an app-written UUID

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** During pairing, the app writes an NDEF message with a custom MIME record containing a generated UUID, plus an Android Application Record (AAR). The tag's hardware UID is stored alongside it.
- **Why:** Scanning launches the app even when it's closed; tags are reusable and reassignable; works identically with HCE test emulation, physical tags, and the debug tag source.
- **Alternatives:** Hardware UID only — rejected because the app must already be open to read it, the identity can't be changed, and HCE randomizes UIDs so it can't be tested without physical tags.
- **Consequences:** The pairing flow must handle write failures. The UUID can be copied to another tag; acceptable for a self-control tool. Optional future strict mode can require the stored UID to match as well.

## D-009 — NTAG215 sticker tags

- **Date:** 2026-10-02
- **Status:** Accepted
- **Decision:** Use NXP NTAG215 stickers (NFC Forum Type 2, ISO 14443A, 504 bytes user memory, 7-byte UID).
- **Why:** Cheap, widely available, readable on practically every NFC-capable Android phone, with password protection and lock bits available for later.
- **Alternatives:** NTAG213 (equally suitable, less memory); MIFARE Classic — rejected because many non-NXP NFC controllers can't read it; NTAG 424 DNA — overkill for personal use.
- **Consequences:** Regular stickers don't read reliably on metal; use on-metal tags for metal surfaces. Don't permanently lock tags during development.

## D-010 — Minimum SDK 30

- **Date:** 2026-10-02
- **Status:** Accepted
- **Supersedes:** D-002
- **Decision:** `minSdk = 30` (Android 11).
- **Why:** All test devices run Android 11 or newer. Package visibility (`<queries>`), background activity start limits and foreground service types become baseline behaviour instead of version branches, which shrinks the test matrix for the AccessibilityService and block screen.
- **Alternatives:** minSdk 26 (D-002) — no meaningful benefit for personal use with these devices.
- **Consequences:** Devices on Android 8–10 can't run the main app. A separate HCE tag-emulator test app can keep its own lower minSdk so older spare phones remain usable as test tags.

## D-011 — HCE tag emulator as a separate app module

- **Date:** 2026-10-05
- **Status:** Accepted
- **Decision:** Until physical tags are available, a second phone emulates a tag via Host Card Emulation. The emulator is its own application module, `:hce-emulator` (`nl.nulldevelopment.nfcappblocker.hceemulator`), with `minSdk = 30`.
- **Why:** Lets the real NFC stack on the reader phone (dispatch, AAR, cold launch, reader mode) be tested end to end without stickers. A separate app keeps test tooling out of the main app.
- **Alternatives:** A lower minSdk for older spare phones, as D-010 allowed — not needed, all spare phones run Android 11 or newer. A debug-only screen inside `:app` — rejected because the emulating phone and the reading phone must be different devices.
- **Consequences:** HCE randomizes the UID on every tap, so UID-based behaviour (strict mode) can only be tested with physical tags. HCE needs the emulating phone's screen to be on. The emulator answers on the lock screen (`requireDeviceUnlock="false"`).

## D-012 — Shared tag format in a pure Kotlin/JVM module

- **Date:** 2026-10-05
- **Status:** Accepted
- **Decision:** The tag format (MIME type, AAR package, payload encoding, NDEF encoding and decoding) lives in `:core:tag-format`, a Kotlin/JVM module without Android dependencies, used by both `:app` and `:hce-emulator`. It contains its own small NDEF codec instead of using `android.nfc.NdefRecord`.
- **Why:** One definition of the format means the emulator and the app can't drift apart. `android.nfc` classes are stubs in local unit tests, so a JVM module can be tested byte for byte without Robolectric or a device.
- **Alternatives:** An Android library built on `android.nfc` — less code, but tests need Robolectric or instrumentation. A copy of the format in each module — rejected because of drift.
- **Consequences:** The app converts with `NdefMessage.toByteArray()` / `NdefMessage(bytes)` at the Android boundary. The codec rejects chunked records; this app never writes them.

## D-013 — Tag payload format v1 and record order

- **Date:** 2026-10-05
- **Status:** Accepted
- **Decision:** The MIME record's payload is one version byte (`0x01`) followed by the 16 bytes of the UUID, most significant bits first (17 bytes). The NDEF message has the MIME record first and the Android Application Record last.
- **Why:** The version byte lets the format change later without guessing what an old tag holds; raw bytes are compact. Android matches intent filters against the first record and recommends putting the AAR last.
- **Alternatives:** 16 raw bytes without a version; the 36-character UUID string, which is readable in generic NFC apps but has no versioning.
- **Consequences:** A tag with an unknown version is reported as "unsupported version", not as foreign, so a newer format can get a clear message instead of a re-pair prompt.

## D-014 — JUnit 5 for local unit tests, without the android-junit5 plugin

- **Date:** 2026-10-05
- **Status:** Accepted
- **Decision:** Local unit tests in all modules use JUnit Jupiter 5.x. Android modules enable it with `testOptions.unitTests.all { it.useJUnitPlatform() }`; JVM modules natively. Instrumented tests stay on the AndroidX JUnit 4 runner.
- **Why:** AGP's local unit tests are ordinary Gradle test tasks, so no plugin is needed. The mannodermaus plugin (now `android-junit-framework`) mainly adds JUnit 5 for instrumented tests, and its AGP 9 support has caveats.
- **Alternatives:** The mannodermaus plugin; JUnit 4 now and migrate later; JUnit 6, which needs a Java 17 runtime target.
- **Consequences:** Revisit if instrumented tests ever need Jupiter.

## D-015 — Use the delivered NTAG215-compatible clone stickers for development

- **Date:** 2026-10-06
- **Status:** Accepted
- **Refines:** D-009
- **Decision:** The stickers that arrived are reported as non-genuine by NXP TagInfo. Use them for development anyway, and verify per batch what the app relies on (NDEF read and write, capacity) instead of assuming genuine NXP behaviour. Take capacity from what the tag itself reports, not from the NTAG215 datasheet.
- **Why:** The app only needs standard NDEF read and write and about 104 bytes (D-013), which clones normally handle. Waiting for genuine tags would block spike B for no clear gain.
- **Alternatives:** Order genuine NXP NTAG215 tags from a known reseller now and pause until they arrive.
- **Consequences:** Clones differ most in password protection, lock bits and the originality signature. Those features are on the "Later" list (password protection, strict mode) and must be tested on the tags actually used then, which may mean buying genuine ones. A blank clone may arrive unformatted, so writing must handle both formatted (`Ndef`) and blank (`NdefFormatable`) tags.

## D-016 — One reference test device: Samsung SM-S948B

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** All on-device testing happens on one Samsung SM-S948B (Android 16), which is also the phone that will run the app day to day. The plan's requirement to test enforcement on at least two OEMs is dropped.
- **Why:** It's the only test device available, and the app is for personal use on exactly this phone. Samsung is also one of the stricter OEMs about background work, so passing here is a meaningful bar.
- **Alternatives:** Borrow or buy a second device (e.g. a Pixel) for a second OEM data point.
- **Consequences:** Behaviour on other OEMs is untested; moving to another phone, or ever publishing, means repeating spike A's measurements there. Detecting a disabled or killed AccessibilityService (D-001, Phase 5) matters even more, because it's the safety net for behaviour we haven't seen.
