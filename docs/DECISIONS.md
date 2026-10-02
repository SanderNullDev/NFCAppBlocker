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
