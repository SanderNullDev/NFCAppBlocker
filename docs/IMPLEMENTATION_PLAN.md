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

- NTAG215 NFC stickers (ordered; verify genuine NXP chips with NFC TagInfo on arrival).
- Until they arrive: a second Android phone running the `:hce-emulator` app, which emulates an NFC Forum Type 4 tag via Host Card Emulation (HCE) (D-011), plus a debug tag source in the app.
- Note: HCE randomizes the UID per tap, so UID-based checks (strict mode) can only be tested with physical tags.

## Phase 0 — Risk spikes (throwaway code)

**Spike A: AccessibilityService enforcement (D-001)**
- Listen for `TYPE_WINDOW_STATE_CHANGED` and redirect to a block screen when a blocked package comes to the foreground.
- Measure: time until the block screen appears, behaviour after the app is swiped from recents, behaviour after a reboot.
- Test on at least two OEMs (e.g. Pixel and Samsung); OEM background killing differs a lot.

**Spike B: NFC round trip (D-008)**
- Second phone runs an HCE `HostApduService` serving a Type 4 NDEF tag (AID `D2760000850101`, CC file `E103`, NDEF file `E104`).
- Confirm: reading the custom MIME record, cold app launch via AAR / MIME intent filter, foreground scanning via `NfcAdapter.enableReaderMode()`.
- When the stickers arrive: confirm writing NDEF to NTAG215.

Status (2026-10-05):
- [x] HCE emulator app `:hce-emulator` (D-011) and shared tag format `:core:tag-format` (D-012, D-013), with unit tests for the APDU state machine and NDEF encoding.
- [x] Emulator UI checked on an Android emulator (no NFC there, so no taps).
- [ ] Two-phone test: the emulator answers a real reader. Not yet done.
- [x] Minimal reader in `:app`: `ScanActivity` handles tag dispatch via the MIME intent filter (cold launch and new intent); `MainActivity` scans with reader mode while in the foreground. Both show a debug screen with the result. Checked on an Android emulator only.
- [ ] Two-phone test of the reader: cold launch via AAR, new-intent path, reader mode, UUID matches the emulator's active preset.
- [ ] NTAG215 write test, when the stickers arrive.

**Exit criteria:** enforcement latency and survival are acceptable on test devices, and the NFC launch flow works end to end.

## Phase 1 — Tag pairing

- "Add tag" flow: scan → inspect existing content → confirm overwrite if needed → write UUID + AAR → name the tag → save.
- NDEF message: custom MIME record (e.g. `application/vnd.nulldev.nfcblocker`) carrying the UUID, plus an AAR with the package name. Store the hardware UID alongside for future strict mode.
- Clear error states with retry: tag removed mid-write, read-only tag, unformatted tag, tag too small, NFC turned off.
- Recognize an already-paired tag and offer to re-link instead of creating a duplicate.
- Tag management: list, rename, unlink, "forget and wipe tag".
- Debug tag source (dev menu button + `adb shell am broadcast` receiver) so later phases don't depend on physical scanning.

## Phase 2 — Block profiles

- Installed-app picker using a `<queries>` element for launcher intents (avoids the Play-restricted `QUERY_ALL_PACKAGES`).
- Create / edit / delete profiles.
- Link tags to profiles: many tags → one profile; each tag belongs to exactly one profile (D-003).
- Never-blockable apps: this app, dialer / emergency, Settings, the current launcher.

## Phase 3 — Blocking engine

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
- Detect a disabled or killed AccessibilityService and surface it clearly instead of failing silently.
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
- `:app` — the blocker app (`nl.nulldevelopment.nfcappblocker`, minSdk 30).
- `:core:tag-format` — pure Kotlin/JVM module with the tag format and NDEF codec, shared by `:app` and `:hce-emulator` (D-012).
- `:hce-emulator` — HCE tag emulator test app for a second phone (D-011).
- All Android modules compile against SDK 37.1 (required by core-ktx 1.19 and lifecycle 2.11) and target SDK 36.
