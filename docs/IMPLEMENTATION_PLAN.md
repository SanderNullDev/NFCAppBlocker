# NFC Application Blocker — Implementation Plan

Personal-use Android app: scanning a self-configured NFC tag toggles blocking of a user-chosen set of apps (TapOut-style, homemade). Not planned for Play Store publication, but the design should not rule it out.

See [DECISIONS.md](DECISIONS.md) for the reasoning behind the choices referenced here.

## Scope

### Must-have (MVP)

- **Tag pairing** — write a generated UUID + Android Application Record (AAR) to a tag, name it, and link it to a block profile (D-003, D-008).
- **Block profiles** — a named set of apps chosen from an installed-app picker. Multiple tags can control the same profile (D-003).
- **Scan to toggle** — scanning a paired tag starts or stops blocking for its profile, whether or not the app is open (D-005).
- **Enforcement** — opening a blocked app shows a block screen instead, via an AccessibilityService (D-001).
- **Escape hatch** — stop blocking without a tag via a countdown, default 3 minutes, configurable 1–20 minutes (D-006).
- **Status visibility** — the user can always see whether blocking is active and for which profile.

### Later

- "Unlock for X minutes" as a separate mechanism next to toggle (D-005)
- Multiple simultaneous active profiles (D-004)
- Schedules (automatic blocking during set hours)
- Strict mode (hardware UID must also match the stored UID)
- NTAG215 password protection against overwriting
- Usage stats (e.g. blocked open attempts per day)
- Quick Settings tile and home screen widget
- Play Store compliance (AccessibilityService declaration and justification)

## Test hardware

- NTAG215 NFC stickers (arrived 2026-10-06). NFC TagInfo reports them as non-genuine; used for development anyway (D-015). They arrived without an NDEF message.
- Until they arrive: a second Android phone running the `:hce-emulator` app, which emulates an NFC Forum Type 4 tag via Host Card Emulation (HCE) (D-011), plus a debug tag source in the app.
- Note: HCE randomizes the UID per tap, so UID-based checks (strict mode) can only be tested with physical tags.

## Phase 0 — Risk spikes (throwaway code)

**Spike A: AccessibilityService enforcement (D-001)**
- Listen for `TYPE_WINDOW_STATE_CHANGED` and redirect to a block screen when a blocked package comes to the foreground.
- Measure: time until the block screen appears, behaviour after the app is swiped from recents, behaviour after a reboot.
- Test on the reference device, a Samsung SM-S948B (D-016). Samsung is strict about background work, so also check survival after the phone has been idle for a while.

Status (2026-10-06):
- [x] Spike built in `:app` (`spike.blocking`): AccessibilityService listening for window state changes only, full-screen block activity, hard-coded test list (Instagram, Chrome), on/off switch persisted with DataStore, latency and service lifecycle shown on the main screen.
- [x] On-device test (2026-10-06): blocking works, and keeps working after swiping the app from recents and after a reboot (the service reconnects by itself). The block screen is visible 80–95 ms after the blocked app opens; the first block after a while took 178 ms.
- [x] Block screen look. Instagram is always visible for about one frame before the block screen; the event only arrives after the app has drawn. Tried in turn:
  - Default slide-in: the slide is jarring.
  - No animation: a single frame of Instagram followed by a hard cut, even more jarring.
  - Translucent with background blur: the reference device doesn't support cross-window blur (`ro.surface_flinger.supports_background_blur=0`), so it only dims the app.
  - Opaque with a fade-in: the fade had no visible effect for an activity started from the accessibility service; it looked like an instant cut.
  - **Chosen:** translucent, with the blocked app dimmed behind a solid card. Covering the app reads as deliberate. The app is paused rather than stopped, so it could keep playing audio; accepted.
  - Not tried: blurring a screenshot taken by the accessibility service. It would need the "take screenshots" capability, so it's only worth it if the dimmed look disappoints in daily use.
- [ ] Survival after a long idle period (several hours or overnight). Assumed fine for now, since reboot survival works; to be confirmed.

Findings:
- Force-stopping the app (App info → Force stop, or Android Studio's Run button, which force-stops before installing) makes Android remove the service from the enabled accessibility services. Blocking silently stops until the user turns the service on again. Accepted under D-007, but Phase 5 must detect this and tell the user, also outside the app. During development, install with `./gradlew :app:installDebug`, which updates the app without a force stop and keeps the service enabled.
- Samsung freezes the app process in the background (`FreecessHandler`) and unfreezes it when an accessibility event arrives. Blocking still worked; watch for added latency or missed events in the long-idle test.

**Spike B: NFC round trip (D-008)**
- Second phone runs an HCE `HostApduService` serving a Type 4 NDEF tag (AID `D2760000850101`, CC file `E103`, NDEF file `E104`).
- Confirm: reading the custom MIME record, cold app launch via AAR / MIME intent filter, foreground scanning via `NfcAdapter.enableReaderMode()`.
- When the stickers arrive: confirm writing NDEF to NTAG215.

Status (2026-10-06):
- [x] HCE emulator app `:hce-emulator` (D-011) and shared tag format `:core:tag-format` (D-012, D-013), with unit tests for the APDU state machine and NDEF encoding.
- [x] Emulator UI checked on an Android emulator (no NFC there, so no taps).
- [-] Two-phone HCE test: skipped, the physical stickers arrived first. The emulator stays available for testing several tags quickly.
- [x] Minimal reader in `:app`: `ScanActivity` handles tag dispatch via the MIME intent filter (cold launch and new intent); `MainActivity` scans with reader mode while in the foreground. Both show a debug screen with the result. Checked on an Android emulator only.
- [x] Write spike: "Write test tag" on the scan screen writes our message with a fresh UUID to the next tag in reader mode and verifies it by reading back. Handles formatted and blank tags.
- [x] Sticker test, part 1 (2026-10-06, Samsung SM-S948B, Android 16): writing and verifying works, rewriting a tag works, reader mode reads the new UUID, and the hardware UID stays the same across taps. The clone stickers present as `NfcA, MifareUltralight, Ndef`, so already NDEF-formatted.
- [x] Sticker test, part 2: with the app closed, a tap opens `ScanActivity` through tag dispatch with the right UUID. The process was usually still running, because the enabled accessibility service keeps it alive, so a truly cold start is rare.
- [x] New-intent path: a second tap while `ScanActivity` is open reaches the open screen (confirmed with a physical tap, 2026-10-06). The first try opened a new screen on every tap, because the NFC service starts each scan in a new task; `ScanActivity` now has its own task (`singleTask`).

**Exit criteria:** enforcement latency and survival are acceptable on the reference device (D-016), and the NFC launch flow works end to end. Met on 2026-10-06, except for the long-idle survival check under spike A.

## Phase 1 — Tag pairing

- "Add tag" flow: scan → inspect existing content → confirm overwrite if needed → write UUID + AAR → name the tag → save.
- NDEF message: custom MIME record (e.g. `application/vnd.nulldev.nfcblocker`) carrying the UUID, plus an AAR with the package name. Store the hardware UID alongside for future strict mode.
- Clear error states with retry: tag removed mid-write, read-only tag, unformatted tag, tag too small, NFC turned off.
- Recognize an already-paired tag and offer to re-link instead of creating a duplicate.
- Tag management: list, rename, unlink, "forget and wipe tag".
- Debug tag source (dev menu button + `adb shell am broadcast` receiver) so later phases don't depend on physical scanning.

Status (2026-10-06):
- [x] Room 3 storage for tags (D-017), Hilt and Navigation 3 (D-018).
- [x] Pairing flow (D-019): blank tags written straight away, confirm-and-hold-again before overwriting, adopting our own tags, verified writes, duplicate scans dropped. Unit tested.
- [x] Screens: tag list (start screen), add tag, tag details with rename, forget, and wipe and forget. The Phase 0 test screen moved behind "Test tools".
- [x] Debug tag source over adb (D-020). Used to run the whole flow on an emulator.
- [x] On the Samsung with a physical sticker (2026-10-06): adopting the sticker written during spike B, recognising it on the next tap, wiping it, and pairing it again as a blank tag.
- Not done: re-linking a tag to another profile; profiles arrive in Phase 2.

## Phase 2 — Block profiles

- Installed-app picker using a `<queries>` element for launcher intents (avoids the Play-restricted `QUERY_ALL_PACKAGES`).
- Create / edit / delete profiles.
- Link tags to profiles: many tags → one profile; each tag belongs to exactly one profile (D-003).
- Never-blockable apps: this app, dialer / emergency, Settings, the current launcher.

Status (2026-10-06):
- [x] Profiles in the database (D-021): name, apps by package name, optional tag link. Version 2 migration with a device test.
- [x] App picker with launcher apps only (D-022), search that ignores case and accents, icons, and selected apps that were uninstalled.
- [x] Never-block rule (D-023): this app, phone app, Settings, launchers, plus Google Maps, Gmail and Samsung Email. Shown disabled with the reason.
- [x] Bottom bar with Profiles and Tags, a separate back stack per tab (D-024).
- [x] Profile editor: name, apps, linked tags (moving a tag from another profile asks first), delete with confirmation.
- [x] Linking a tag from its details, and right after pairing.
- Checked on an emulator with simulated tags, and on the Samsung for the database and app list (device tests).
- [x] Follow-up after trying it (2026-10-06): the editor saves explicitly with a Save button and asks before discarding changes (D-025, supersedes D-021); "Scan a tag" in the editor links a paired tag or pairs a new one on the spot.

## Phase 3 — Blocking engine

- Use the never-block rule (D-023) in the blocker, replacing the spike's own list. Refuse to activate a profile without apps (D-021).
- Persist blocking state (active profile(s) + start time) so it survives process death.
- Only one active profile at a time, but model the active state as a collection so multiple active profiles later is a logic change, not a data migration (D-004).
- Enforcement via the AccessibilityService from spike A.
- Block screen: which app was blocked, which profile is active, hint to scan a tag or start the escape countdown. Never trap the user — Home and Back must keep working.
- Restore enforcement after reboot (`BOOT_COMPLETED`) and app updates.
- Onboarding for battery optimization exemption and OEM-specific background restrictions.

## Phase 4 — Scan to toggle

- Paired tag → toggle its profile (D-005). Unknown or foreign tag → pairing flow.
- Debounce so one physical tap that registers twice doesn't toggle on and immediately off.
- Feedback on every scan: haptics, optional sound, and an accessibility announcement of the new state.
- Behaviour when scanning a tag for profile B while profile A is active: see open question Q-1.

## Phase 5 — Escape hatch and hardening

- Countdown escape hatch: default 3 minutes, configurable 1–20 minutes in settings (D-006). Leaving the countdown screen cancels it.
- Detect a disabled or killed AccessibilityService and surface it clearly instead of failing silently. Force stop disables the service (see spike A findings), so this must also reach the user outside the app, e.g. through the status notification.
- No tamper resistance: uninstalling the app or disabling the service is an accepted way out (D-007).

## Phase 6 — Polish

- Material 3 UI, dark theme, dynamic color.
- Accessibility pass: TalkBack labels on all controls, touch targets ≥ 48 dp, no state conveyed by color alone, announced scan results.
- Onboarding: NFC check, accessibility service permission, battery optimization, first tag pairing.
- Persistent status notification while blocking is active.

## Testing strategy (all phases)

- **Unit tests:** JUnit 5 (Jupiter, without the android-junit5 plugin, D-014), MockK, Turbine — toggle logic, profile resolution, debounce, escape-hatch countdown — driven through the fake tag source.
- **NDEF tests:** encode/decode round trips for our records, plus malformed and foreign payloads.
- **Instrumented / manual:** HCE scans, cold launch, reboot persistence, OEM background killing. Keep a short manual test checklist per device; much of this can't be automated reliably.
- **Static analysis:** Android Lint (warnings as errors on new code), detekt, ktlint.

## Open questions

- **Q-1:** Scanning a tag for profile B while profile A is active — switch to B, or reject the scan?
- **Q-2:** Can profiles and the countdown duration be edited while blocking is active? Given D-007 the likely answer is "yes, that's on the user", but it should be decided explicitly.

## Project layout

- `docs/` — this plan, the decision log, and later the architecture document.
- `:app` — the blocker app (`nl.nulldevelopment.nfcappblocker`, minSdk 30). Packages: `data/` (Room, repositories), `feature/<name>/` (screens and ViewModels), `nfc/` (tag reading and writing), `ui/`, `di/`, and `spike/` for Phase 0 code that later phases replace.
- `:core:tag-format` — pure Kotlin/JVM module with the tag format and NDEF codec, shared by `:app` and `:hce-emulator` (D-012).
- `:hce-emulator` — HCE tag emulator test app for a second phone (D-011).
- All Android modules compile against SDK 37.1 (required by core-ktx 1.19 and lifecycle 2.11) and target SDK 36.
