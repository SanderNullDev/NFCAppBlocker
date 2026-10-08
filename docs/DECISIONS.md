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

## D-017 — Room 3 for local storage

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** Store app data (paired tags now, profiles later) in a Room 3 database (`androidx.room3`) using the framework SQLite driver. Schemas are exported to `app/schemas`. The rest of the app uses repository interfaces, not DAOs.
- **Why:** Tags, profiles and their links (D-003) are relational. Room gives Flow-based queries and migrations. Room 3 is the current line for new projects: coroutines-first and KSP-only.
- **Alternatives:** Room 2.8 — still supported, but older APIs for a new codebase. DataStore — fine for settings, not for related records.
- **Consequences:** DAO tests run on a device (instrumented, JUnit 4 per D-014). Logic above the repositories is tested on the JVM with fakes.

## D-018 — App structure: Hilt, Navigation 3, packages in `:app`

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** Dependency injection with Hilt. Screens navigate with Navigation 3, using serializable route keys. Features live in packages inside `:app` (`data/`, `feature/<name>/`, `nfc/`, `ui/`), not in separate Gradle modules.
- **Why:** Hilt is the standard Jetpack DI and was planned for this phase. Navigation 3 is the current Compose navigation library, with the back stack as plain state. Separate modules add build setup with little benefit for a one-person app.
- **Alternatives:** Manual DI; Navigation Compose 2; feature modules from the start.
- **Consequences:** Hilt and Room need KSP; they were verified to build with AGP 9's built-in Kotlin before feature code relied on them. Packages can be split into modules later if build times grow.

## D-019 — Tag pairing rules

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:**
  - A blank tag is written and saved straight away under a default name ("Tag N", first unused number); renaming comes after.
  - A tag with other content is only overwritten after the user confirms and then holds the same tag (same hardware UID) again.
  - A tag with our UUID that isn't stored can be adopted without writing; if it's writable, it can instead get a new UUID.
  - A write is only stored after reading it back confirms our content.
  - A scan that arrives while another is being handled is dropped, not queued.
  - "Wipe and forget" only wipes the tag being viewed, verified by its UUID.
- **Why:** Saving right after the write means a cancelled naming step can't leave a written tag the app doesn't know. The confirm-and-hold-again rule keeps a different tag from being overwritten by accident. Adopting covers app data being cleared, tags written during the spikes, and read-only tags such as the HCE emulator. Dropping scans stops one tap that registers twice from acting twice.
- **Alternatives:** Name first, then write; overwrite right after confirming with the tag still in the field; treating every tag of ours that isn't stored as foreign.
- **Consequences:** The pairing logic is a plain state machine (`PairingFlow`), tested without Android.

## D-020 — Debug tag source over adb

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** Debug builds include a broadcast receiver that simulates holding a tag against the phone: blank, foreign, unreadable or one of our UUIDs, optionally read-only. Simulated tags keep what's written to them. The receiver requires the `DUMP` permission, so only adb and the system can send it, and release builds don't contain it.
- **Why:** Pairing and the later scan-to-toggle phases can be tested on an emulator and scripted, without a physical tag for every check.
- **Alternatives:** A debug menu in the app; relying on the HCE emulator, which needs a second phone and is read-only.
- **Consequences:** Real and simulated scans share one path (`NfcTagEvents`), so the debug source tests the same code a real tap runs.

## D-021 — Profile data model and saving as you edit

- **Date:** 2026-10-06
- **Status:** Superseded by D-025
- **Decision:** A profile has a name and a set of apps, stored by package name (`profiles`, `profile_apps`). A tag links to at most one profile through a nullable `profile_id`. Deleting a profile keeps its tags and only unlinks them. "New profile" creates the profile straight away under a default name ("Profile N"), and every edit saves immediately; there's no separate save step.
- **Why:** Package names are stable, while names and icons can change, so those are looked up live. Tags paired in Phase 1 have no profile yet, and a later scan of an unlinked tag can simply ask which profile it should control. Saving as you go matches tag pairing (D-019) and leaves nothing half-saved to lose.
- **Alternatives:** Requiring a profile when pairing a tag; an explicit Save button with a "discard changes?" prompt.
- **Consequences:** An empty profile can exist; Phase 3 must refuse to activate a profile without apps. The database moved to version 2 with an automatic migration, covered by a device test.

## D-022 — App picker shows launcher apps only

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** The app picker lists apps that have a launcher icon, found through a `<queries>` entry for the launcher intent. The app doesn't request `QUERY_ALL_PACKAGES`. Apps that are selected but no longer installed stay in the list, marked "Not installed", so they can be removed.
- **Why:** Launcher apps are what users think of as their apps. Google Play restricts `QUERY_ALL_PACKAGES`, and listing all packages would bury the picker in system components.
- **Alternatives:** All installed packages.
- **Consequences:** An app without a launcher icon can't be picked. That's acceptable for blocking distractions.

## D-023 — Apps that can never be blocked

- **Date:** 2026-10-06
- **Status:** Accepted
- **Decision:** One shared rule (`NeverBlockPolicy`) says which apps are never blocked: this app, the default phone app, Settings, and every launcher, plus Google Maps, Gmail and Samsung Email, which the user wants available at all times. The phone app and launchers are read live. The picker lists these apps disabled, with the reason in words; an app that's already selected can still be removed.
- **Why:** The phone app keeps emergency calls possible, and Settings and the launcher mean the user is never trapped (plan, Phase 3: "never trap the user"). Showing the reason instead of hiding the app makes clear why it can't be picked.
- **Alternatives:** Hiding protected apps from the picker; a user-editable allow list (possible later).
- **Consequences:** The blocker in Phase 3 must use the same rule, replacing the spike's own list.

## D-024 — Bottom bar with Profiles and Tags

- **Date:** 2026-10-06
- **Status:** Superseded by D-029
- **Decision:** The app's top level is a bottom bar with two tabs, Profiles (the start tab) and Tags. Each tab keeps its own back stack; the user always leaves the app through the Profiles tab. Phase 3 can add a third tab for the current blocking status.
- **Why:** Profiles and tags are both managed often and are equal in importance; tags without a profile need a home of their own.
- **Alternatives:** One start screen with profiles, reaching tags only through a profile.
- **Consequences:** Built after the Navigation 3 multiple-back-stacks recipe (D-018).

## D-025 — Profile editor saves explicitly

- **Date:** 2026-10-06
- **Status:** Accepted
- **Supersedes:** D-021
- **Decision:** The data model from D-021 stays: a profile has a name and a set of apps stored by package name, a tag links to at most one profile, and deleting a profile keeps its tags but unlinks them. What changes is saving: the profile editor works on a draft (name, apps, linked tags) and stores it only when the user presses Save, in one transaction. Leaving with unsaved changes asks whether to discard them. "New profile" creates nothing until it's saved. Deleting stays immediate, after a confirmation.
- **Why:** Trying the editor showed that saving silently in the background left it unclear whether leaving the screen kept the changes. An explicit Save matches what the user expects, and it means backing out of "New profile" leaves no empty profile behind.
- **Alternatives:** Keep saving as you edit, but show a "Saved" indicator and a Done button (D-021).
- **Consequences:** Tag pairing still saves straight away (D-019). A tag paired from inside the profile editor is stored at once and only linked to the profile when the profile is saved. Phase 3 must still refuse to activate a profile without apps.

## D-026 — Scan to toggle, and Phase 3 and 4 combined

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** Phase 3 (blocking engine) and Phase 4 (scan to toggle) are built together. Scanning a tag linked to a profile starts that profile; scanning it again (or any other tag of that profile) stops it. Scanning a tag of another profile while one is active switches to that profile (answers open question Q-1). The same tag within 2 seconds is ignored. Tags that can't toggle anything are handled helpfully: an unlinked tag opens its details, an unknown tag of ours opens "Add tag", and a profile without apps isn't started. Each scan gives a short message and a vibration, distinct for starting and stopping.
- **Why:** Without scan to toggle, Phase 3 would need a temporary start/stop button; the scan path and the debug tag source already existed. Switching does what the user most likely means with one tap.
- **Alternatives:** A temporary button in Phase 3 and scanning in Phase 4; refusing a scan of another profile's tag while one is active ("scan one of its tags to stop it").
- **Consequences:** Any linked tag can end the current block. That's accepted under D-007 (no tamper resistance). The 2-second window is a guess, to be tuned in daily use.

## D-027 — Editing an active profile is allowed

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** A profile that's blocking can be edited, and saved changes apply straight away; the editor says the profile is blocking. Deleting the active profile stops blocking, and the confirmation says so (answers open question Q-2).
- **Why:** Locking the editor would only add friction for a personal tool without tamper resistance (D-007).
- **Alternatives:** Lock editing and deleting while a profile is active.
- **Consequences:** Removing apps from an active profile is an easy way around a block. That's a deliberate choice by the user, as D-007 accepts.

## D-028 — Routing scans: screens claim them, otherwise they toggle

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** While the app is open, a scan goes to a screen that needs a tag (adding, wiping, scanning in the profile editor) only while that screen is visible and waiting; it claims scans for that time. Every other scan toggles blocking. With the app closed, the scan opens an invisible activity that toggles, gives feedback and closes, opening the app only when the tag can't toggle anything yet.
- **Why:** Claiming only while visible means a screen left in another tab's history can never take a scan, for example a hidden "Add tag" writing to a tag being scanned in the profile editor.
- **Alternatives:** Every listening screen gets every scan; a fixed priority between screens.
- **Consequences:** Real and simulated scans take the same route (D-020), so the debug tag source tests toggling too.

## D-029 — Status tab as the start screen

- **Date:** 2026-10-07
- **Status:** Superseded by D-043
- **Supersedes:** D-024
- **Decision:** The bottom bar has three tabs: Status (the start tab), Profiles and Tags, each with its own back stack; the user leaves the app through Status. Status shows whether blocking is on, which profile, since when and which apps, warns when the accessibility service is off (urgently while a profile is active), asks for notification permission, and points to what still needs setting up.
- **Why:** Whether blocking is on is the first thing to see when opening the app (plan: "the user can always see whether blocking is active").
- **Alternatives:** Keep Profiles as the start tab (D-024).
- **Consequences:** Profiles and Tags keep working as described in D-024.

## D-030 — Ongoing notification while blocking

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** While a profile is blocking, an ongoing, silent notification shows the profile, since when and which apps; tapping it opens the app. It needs the notification permission on Android 13+, which the Status tab asks for. Brought forward from Phase 6.
- **Why:** Status must be visible outside the app too, including after toggling with the app closed.
- **Alternatives:** Status only inside the app; a foreground service (not needed: the accessibility service does the enforcing).
- **Consequences:** Phase 5's warning about a disabled accessibility service can use the same notification.

## D-031 — How the escape countdown works

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** Works out the countdown from D-006. "Stop without a tag" is on the Status tab and on the block screen, only while a profile is blocking. The countdown has its own screen, which keeps the display on. Leaving the screen (Back, Home, another app) or turning off the display cancels it, and so does blocking stopping or switching to another profile in the meantime. When it finishes, it stops the profile it was started for, with the same message and vibration as a tag. TalkBack hears the minutes left once a minute, not every second. Cancelling from the block screen goes to the home screen. When the countdown finishes there, the app that was blocked comes back.
- **Why:** The countdown is meant as friction, so it should take attention for its whole length rather than run in the background. Keeping the display on means the screen timeout can't cancel it by accident.
- **Alternatives:** Only start it from the Status tab; let it keep running when the screen is left, or pause it.
- **Consequences:** A phone call or anything else that covers the screen cancels the countdown, and the user starts it again.

## D-032 — Countdown length locked while blocking

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** The countdown length can be changed in Settings only while nothing is blocking. While a profile is blocking, the setting is disabled with an explanation, and changes are refused (answers the rest of open question Q-2).
- **Why:** Otherwise the way out of a block is to set the countdown to 1 minute and start it, which makes the chosen length pointless. Unlike editing a profile (D-027), this setting exists only as friction.
- **Alternatives:** Allow changes at any time, as with profiles (D-027).
- **Consequences:** This is friction, not tamper resistance: under D-007 the user can still turn off the accessibility service.

## D-033 — Warning when the accessibility service is off

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** While a profile is blocking and the accessibility service is off, the ongoing notification (D-030) says the profile isn't blocking anything and that the service is off; tapping it opens the app, where the Status tab has the button to fix it (D-029). The service reports being switched off from `onUnbind`, and the app checks the service at start and whenever the Status tab is shown. When the service comes back, the normal text returns.
- **Why:** Force stop turns the service off without telling the user (Phase 0, spike A). Blocking then silently stops while the app still says it's blocking.
- **Alternatives:** A periodic check with WorkManager; a separate, more urgent notification.
- **Consequences:** If the service is turned off while the app's process isn't running, for example by force stop, the warning appears only the next time the app starts. A scan of a tag starts the app, so that case is covered too.

## D-034 — Setup checklist on the Status tab

- **Date:** 2026-10-07
- **Status:** Superseded by D-037
- **Decision:** The Status tab shows a "Finish setting up" checklist: NFC, the blocker service, notifications, a profile with apps, and a linked tag. Each step shows whether it's done (as text for TalkBack, not only as a check mark), and steps still to do have a button. The checklist disappears once everything is done and comes back when a step is undone. Before opening Accessibility settings, a dialog explains what the service looks at and what it doesn't. It replaces the separate notices and hints from D-029; while blocking with the service off, the blocking card says it isn't working.
- **Why:** Builds on what the Status tab already showed, and keeps working after setup, for example when a force stop turns the service off. Explaining the service before sending the user to Settings is also what Google Play expects of an accessibility service.
- **Alternatives:** A first-run wizard, shown once; both a wizard and the checklist.
- **Consequences:** Battery optimisation isn't a step; it's only added if the long-idle test shows Samsung stopping the service. A friendlier setup is planned for later.

## D-035 — Phase 6 look: dark window, stand-in colours, own icon

- **Date:** 2026-10-07
- **Status:** Superseded by D-038
- **Decision:** The window behind the Compose screens follows dark mode, so the app doesn't flash white when it opens. Dynamic colour stays; devices without it get a Material 3 scheme from a teal seed instead of the template's purple. The app icon is a white padlock with the NFC symbol on teal, with the symbol cut out so the themed (monochrome) icon keeps it, plus a matching status bar icon for the notification.
- **Why:** Fixes what looked broken (the white flash, the template icon, the notification icon showing as a blob) without designing a full theme yet.
- **Alternatives:** Only the theme fixes, keeping the template icon.
- **Consequences:** The teal colours and the icon are a stand-in until the theming redesign planned for later.

## D-036 — Accessibility pass postponed until a release

- **Date:** 2026-10-07
- **Status:** Deferred
- **Decision:** The full accessibility pass from Phase 6 (automated accessibility checks in device tests, a TalkBack walkthrough at large font sizes, and the fixes from both) is postponed until the app might be released. What was built in along the way stays: TalkBack labels, touch targets of at least 48dp, live regions for changing state, and state shown as text rather than by colour alone.
- **Why:** The app is for personal use for now, and its users don't rely on TalkBack or large fonts. The pass is worth doing before others use it.
- **Alternatives:** Do the pass now (automated checks plus a manual walkthrough), or only the manual walkthrough.
- **Consequences:** Accessibility problems the per-screen work missed can go unnoticed until then. New screens keep following the accessibility-first rules in CLAUDE.md.

## D-037 — First-run onboarding, with the checklist as a safety net

- **Date:** 2026-10-07
- **Status:** Superseded by D-042
- **Supersedes:** D-034
- **Decision:** On first launch the app shows full-screen onboarding steps with a progress bar: welcome; switch on the blocker service (with the explanation, and help for Android's "restricted settings" on apps not installed from the Play Store; can't be skipped); add a tag (checking NFC first; naming is optional); notifications (can be skipped); choose the apps to block, which creates a block list linked to that tag; try it by tapping the tag (can be skipped). Progress is saved, so leaving halfway resumes at the same step. Onboarding is skipped when the app is already set up (the service is on and a tag is linked to a block list with apps). The Status checklist from D-034 stays as a safety net for steps undone later, such as the service turned off by a force stop.
- **Why:** The app should be set up correctly by someone who isn't technical. One task per screen, in a fixed order, with the most important step first, is easier to follow than a checklist with several open steps.
- **Alternatives:** Keep only the checklist (D-034); keep linking the tag to a block list as a separate step.
- **Consequences:** Pairing and the app picker are reused inside onboarding. Battery optimisation still isn't a step (see D-034).

## D-038 — Visual design: broken white with a sage accent

- **Date:** 2026-10-07
- **Status:** Superseded by D-045
- **Supersedes:** D-035
- **Decision:** A light, minimal look: a broken-white background with a sage green accent, dynamic colour off so the app looks the same on every phone, and a muted dark version with a warm charcoal background that follows the system setting. The system font stays. Existing screens get the palette, more whitespace and fewer heavy cards, with the same layout and navigation. The app icon (D-035's padlock with the NFC symbol) moves to the sage accent; the dark window from D-035 stays.
- **Why:** The app is meant to reduce phone use, so it should feel calm and quiet rather than busy. Sage was the calmest of the three accents tried (sage, terracotta, dusty blue).
- **Alternatives:** Terracotta or dusty blue; keeping dynamic colour; light only; bundling a softer font such as DM Sans or Nunito.
- **Consequences:** Wallpaper colours no longer change the app's colours.

## D-039 — "Block list" instead of "profile"

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** The UI calls a profile a "block list" ("blokkeerlijst" in Dutch), including the tab name. The code and the database keep `Profile`.
- **Why:** "Block list" says what it is; "profile" is a technical word.
- **Alternatives:** Keep "profile".
- **Consequences:** Earlier decisions keep saying "profile"; it means the same thing.

## D-040 — Dutch, and choosing the app's language

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** The app is translated into Dutch and follows the phone's language: Dutch on a Dutch phone, otherwise English. Settings has a language option (system default, English, Nederlands) using Android's per-app languages, so it also shows up in Android's own app language setting. That feature exists from Android 13; on Android 11 and 12 the option is hidden and the app follows the system. Dutch terms match what Samsung's own Settings use, such as "Toegankelijkheid" and "Geïnstalleerde apps".
- **Why:** Both users speak Dutch and want the app in Dutch.
- **Alternatives:** AppCompat's language API, which also works on Android 11 and 12 but needs AppCompat activities.
- **Consequences:** Every new text needs a Dutch version; lint reports missing translations. Translations are done last in Phase 7, so new texts are translated once.

## D-041 — The app is called TikStop

- **Date:** 2026-10-07
- **Status:** Accepted
- **Decision:** The app's name is TikStop, written as one word with two capitals, in English and Dutch: "tik" (tap) plus "stop". It replaces "NFC App Blocker" in every text the user sees, including the launcher and the accessibility service's name in Settings. The package name (`nl.nulldevelopment.nfcappblocker`) and the project stay as they are.
- **Why:** Short and easy to say in both languages, it says what you do, and it fits the main use, cutting down TikTok.
- **Alternatives:** PauseTap, Tapaway, Stil, Rust, Slot.
- **Consequences:** Changing the package name would install a separate app and lose existing data, so it stays. Check the Play Store and trademarks before any release.

## D-042 — Onboarding has seven steps

- **Date:** 2026-10-08
- **Status:** Superseded by D-047
- **Supersedes:** D-037
- **Decision:** Everything in D-037 stays, with one step added before "Try it": "How to stop blocking". It explains tapping the tag again and unlocking without a tag (the countdown, which leaving cancels while the block list stays active), and sets the countdown length. "Try it" lists the apps that will be blocked. The steps: welcome, switch on blocking, add your tag, notifications, choose apps to block, how to stop blocking, try it.
- **Why:** The user should know how to get out of a block before starting one. The countdown length can't be set during "Try it", because it's locked while blocking (D-032).
- **Alternatives:** Explain stopping and set the length on the "Try it" step.
- **Consequences:** The Status checklist from D-034 still serves as the safety net.

## D-043 — One home screen instead of tabs

- **Date:** 2026-10-08
- **Status:** Superseded by D-046
- **Supersedes:** D-029
- **Decision:** The app has one home screen and no tab bar. Top to bottom: the setup checklist (only while something isn't done), the blocking card, four numbers with a Today / Week / All time switch (D-044), and the block lists, each showing its apps and tags, which open the editor when tapped. A gear at the top right opens Settings. An Add button offers "New block list" and "Add tag". Tags are managed from the editor (tapping a linked tag opens its details) and from "Tags" in Settings, which lists all tags, also unlinked ones. The button that starts the countdown is called "Unlock now" ("Nu ontgrendelen"); other texts refer to it by that name.
- **Why:** A single screen is easier to follow for someone who isn't technical, and tags are rarely managed once set up.
- **Alternatives:** Keep the three tabs (D-029); a Tags section on the home screen; an Add button for block lists only.
- **Consequences:** Back from any screen returns to the home screen. The labels of D-029's Status tab carry over to the home screen.

## D-044 — Usage history and the numbers on the home screen

- **Date:** 2026-10-08
- **Status:** Accepted
- **Decision:** The app records blocking sessions (block list, start, end, and how it ended: tag, unlock without a tag, switching lists, or the list deleted) and each time the block screen is shown (when, which app), in the database, on the phone only. The home screen shows, for today, the last 7 days or all time: time blocked (a running session counts up to now), times blocked (sessions started), apps stopped (block screens shown) and stopped without a tag (countdowns that finished). History starts when this is installed; nothing is filled in from before.
- **Why:** Seeing the numbers shows whether the app helps, which is the point of using it.
- **Alternatives:** All time only; today only; storing counters instead of events.
- **Consequences:** The database moves to version 4 with a migration. The history grows over time; it's small (one row per session or block screen), so there's no clean-up for now.

## D-045 — Figtree, soft cards and an app identity

- **Date:** 2026-10-08
- **Status:** Accepted
- **Supersedes:** D-038
- **Decision:** D-038's colours stay (broken white, sage, warm charcoal in dark mode, no dynamic colour). The text uses Figtree, bundled with the app (SIL Open Font License). Cards are Material cards without a border, lifted by a soft, low-contrast shadow; in dark mode they're one step lighter than the background instead. The home screen starts with a wordmark: the sage padlock and "TikStop" in Figtree. It scrolls with the rest of the screen rather than staying fixed.
- **Why:** The system font and bordered cards made the app look generic and busy. A friendly, readable typeface and soft cards suit an app about calm. A fixed header while the content scrolls felt jarring.
- **Alternatives:** Nunito (rounder, more playful), DM Sans (more businesslike), the system font; a fixed top bar.
- **Consequences:** The font adds about 100 KB to the app. Screens other than home keep their top bars for now.

## D-046 — A calm home screen

- **Date:** 2026-10-08
- **Status:** Accepted
- **Supersedes:** D-043
- **Decision:** D-043's structure stays: one screen, no tab bar, the gear, the Add button, block lists that open the editor, tags in Settings, and "Unlock now". What changes: a greeting with one sentence on how things stand; problems show as one slim banner with the most urgent issue and a fix button, which opens the full checklist; a missing NFC chip and notifications don't show on the home screen, because the app can't fix the first and works without the second; the status card is quiet when nothing blocks and sage while blocking, with the time so far; of the numbers, only time blocked shows by default, and "Details" folds out the other three inside the same card. Hint texts that repeat aren't shown.
- **Why:** The home screen showed everything at once, worst when something was wrong (on the emulator, a checklist for NFC it can't have). One status, one number and one problem at a time is easier to take in.
- **Alternatives:** Keep the checklist and four equal tiles (D-043); drop the other three numbers from the screen.
- **Consequences:** Notifications and NFC status move to Settings. The texts will be reviewed once the layout runs on the emulator.

## D-047 — The apps step of onboarding can be skipped

- **Date:** 2026-10-08
- **Status:** Accepted
- **Supersedes:** D-042
- **Decision:** D-042's seven steps stay, and choosing apps to block can now be skipped too ("Voor nu overslaan"). The step says it can also be done later from the home screen. After skipping, "How to stop blocking" still shows, and onboarding ends there: "Try it" needs a block list. The home screen then asks for one with its problem banner (D-046). The skip buttons on the other steps use the same wording.
- **Why:** Not everyone knows which apps to block during setup; being stuck on that step until something is ticked is worse than coming back to it later.
- **Alternatives:** Keep the step required (D-042).
- **Consequences:** The tag paired during onboarding stays without a block list until one is made.
