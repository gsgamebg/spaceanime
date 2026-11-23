# SpaceAnime Player Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete episode navigation, direct Home navigation, optional auto-next, and trusted intro/outro skipping on Android and Windows.

**Architecture:** Keep `PlayerScreen` shared across platforms and preserve Media3/VLC as native playback engines. Persist the auto-next preference in the existing profile store; load per-episode skip markers from a narrowly readable Supabase RPC.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Media3, VLCJ, Supabase PostgreSQL/RPC, Kotlin tests.

**Spec:** [2026-11-17-spaceanime-player-updates-ads-design.md](../specs/2026-11-17-spaceanime-player-updates-ads-design.md)

## Global Constraints

- Implement player UI and navigation for both Android and Windows; do not replace Media3 or VLCJ.
- Auto-next defaults to off and cannot run without a valid next episode.
- Do not guess intro/outro timestamps; show skip actions only for validated markers.
- Android controls use at least 48 dp touch targets, accessible labels, and adequate spacing.
- Keep profile JSON versions 1 and 2 readable; missing auto-next preference defaults to false.

## Review Focus

- Missing or stale queue: navigation and auto-next must not invent an episode.
- Malformed or out-of-duration marker: no skip action or seek beyond duration.
- Changing episode while a marker request is in flight: old response must not apply to the new episode.
- Legacy profile JSON: defaults must preserve existing profile and reduce-motion data.
- Player back/Home and full-screen: back returns to caller; Home clears player and selects Home.

---

### Task 1: Auto-next decision rule

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/anim/player/PlayerInteractions.kt`
- Test: `composeApp/src/commonTest/kotlin/app/anim/player/PlayerInteractionsTest.kt`

**Interfaces:**
- Produces `shouldAutoAdvance(autoPlayNext: Boolean, ended: Boolean, hasNext: Boolean): Boolean`.

- [ ] **Step 1: Write the failing test**

Add `autoAdvanceRequiresPreferenceEndedStateAndNextEpisode` asserting true only when all three inputs are true, including false for each missing condition.

- [ ] **Step 2: Run the test and verify it fails**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.PlayerInteractionsTest`
Expected: compilation/test failure because `shouldAutoAdvance` is not implemented.

- [ ] **Step 3: Implement the pure decision rule**

Implement `shouldAutoAdvance` in `PlayerInteractions.kt` as the conjunction of the three arguments.

- [ ] **Step 4: Run the test and verify it passes**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.PlayerInteractionsTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/player/PlayerInteractions.kt composeApp/src/commonTest/kotlin/app/anim/player/PlayerInteractionsTest.kt
git commit -m "test: define player auto-advance behavior"
```

### Task 2: Persist the per-profile auto-next preference

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/anim/profile/Profiles.kt`
- Modify: `composeApp/src/jvmSharedMain/kotlin/app/anim/profile/FileProfiles.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/profile/ProfileScreen.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/profile/FileProfilesTest.kt`

**Interfaces:**
- Produces `LocalProfile.autoPlayNext: Boolean = false` and `Profiles.setAutoPlayNext(value: Boolean)`.
- Profile persistence advances to JSON version 3; versions 1 and 2 remain readable.

- [ ] **Step 1: Write failing migration and persistence tests**

Add tests that load v1 and v2 JSON with `autoPlayNext == false`, then save true, reopen the store, and observe true without changing other profile fields.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.profile.FileProfilesTest`
Expected: compilation or assertion failure for the missing preference/API.

- [ ] **Step 3: Add the preference to the profile model and file format**

Add `autoPlayNext` with a false default, serialize as JSON version 3, and read it only when present and valid. Extend the `Profiles` interface and `FileProfiles`.

- [ ] **Step 4: Add a profile setting**

Add a labeled switch in `ProfileScreen.kt` that calls `setAutoPlayNext` and follows the existing busy/error pattern.

- [ ] **Step 5: Run the tests and verify they pass**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.profile.FileProfilesTest`
Expected: `BUILD SUCCESSFUL`; older profile migration and round-trip tests pass.

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/profile/Profiles.kt composeApp/src/jvmSharedMain/kotlin/app/anim/profile/FileProfiles.kt composeApp/src/commonMain/kotlin/app/anim/profile/ProfileScreen.kt composeApp/src/desktopTest/kotlin/app/anim/profile/FileProfilesTest.kt
git commit -m "feat: persist player auto-next preference"
```

### Task 3: Store and safely read episode markers

**Files:**
- Create: `supabase/migrations/202611170003_playback_markers.sql`
- Create: `composeApp/src/commonMain/kotlin/app/anim/player/EpisodeMarkersClient.kt`
- Create: `composeApp/src/jvmSharedMain/kotlin/app/anim/player/EpisodeMarkersHttp.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/player/EpisodeMarkersHttpTest.kt`

**Interfaces:**
- Produces `data class EpisodeMarkerKey(val sourceId: String, val contentId: String, val episodeId: String)`.
- Produces `interface EpisodeMarkersClient { suspend fun load(key: EpisodeMarkerKey): EpisodeMarkers? }`.
- Produces `expect fun createEpisodeMarkersClient(): EpisodeMarkersClient`.
- Marker keys are `(source_id, content_id, episode_id)`; supported anime sources are `anitube` and `animesdigital`.

- [ ] **Step 1: Write failing parser tests**

Add `validMarkerPayloadParsesOnlyCompleteIntervals` and `invalidMarkerPayloadIsRejected`, covering null intervals, negative/reversed bounds, invalid keys, and malformed JSON.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.EpisodeMarkersHttpTest`
Expected: compilation failure for the missing parser/client.

- [ ] **Step 3: Add the constrained marker table and read RPC**

Create a private marker table with a composite primary key, source allowlist, length checks, and paired/nonnegative interval constraints. Add a public read-only RPC restricted to one exact key; grant execution to `anon` and `authenticated`, but no direct table access. Initial writes are manual through the Supabase dashboard/service role.

- [ ] **Step 4: Implement the bounded Supabase client**

Implement the expect/actual factory in `jvmSharedMain` using the existing HTTPS Supabase project URL and publishable key. Validate identifiers before the request, cap the response body at 16 KiB, validate returned source/content/episode keys, and convert only valid start/end pairs to `Segment`.

- [ ] **Step 5: Run parser tests**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.EpisodeMarkersHttpTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Apply the migration in the development Supabase project**

Verify anonymous reads return only the requested marker and direct anonymous table reads/writes are denied. Do not apply to production until this check succeeds.

- [ ] **Step 7: Commit**

```powershell
git add supabase/migrations/202611170003_playback_markers.sql composeApp/src/commonMain/kotlin/app/anim/player/EpisodeMarkersClient.kt composeApp/src/jvmSharedMain/kotlin/app/anim/player/EpisodeMarkersHttp.kt composeApp/src/desktopTest/kotlin/app/anim/player/EpisodeMarkersHttpTest.kt
git commit -m "feat: add trusted episode skip markers"
```

### Task 4: Wire player navigation and marker loading

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/anim/player/PlayerScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/App.kt`
- Test: `composeApp/src/commonTest/kotlin/app/anim/player/PlayerInteractionsTest.kt`

**Interfaces:**
- `PlayerScreen` consumes `autoPlayNext: Boolean = false` and `onHome: () -> Unit`.
- `App` supplies the active profile preference and an `onHome` callback that clears `source`/`playerQueue`, sets `screen = SpaceScreen.Home`, and sets `homeFocus = HomeNavFocus.Home`.
- Marker loading uses the Task 3 `EpisodeMarkerKey` and `shouldApplyEpisodeMarkerResult`.
- Produces `shouldApplyEpisodeMarkerResult(requested: EpisodeMarkerKey, current: EpisodeMarkerKey): Boolean`.

- [ ] **Step 1: Write failing behavior tests**

Add `markerResponseAppliesOnlyToActiveEpisode`, asserting a response for the current queue item is accepted and a response for a previous item is discarded.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.PlayerInteractionsTest`
Expected: compilation failure because `shouldApplyEpisodeMarkerResult` is not implemented.

- [ ] **Step 3: Add explicit Home navigation and safe next-episode behavior**

Keep the existing Back and previous/next actions. Add a labeled Home action. Use the Task 1 predicate to trigger the next episode at most once per queue item when the preference is on; keep the end-of-episode button when it is off.

- [ ] **Step 4: Implement the stale-response guard and load markers**

Implement `shouldApplyEpisodeMarkerResult` as exact key equality. Load markers using the queue anime source/id and current episode ID, key the effect by those values, and discard stale results when the key changes. Preserve any valid markers already supplied by `PlaybackSource`.

- [ ] **Step 5: Run desktop tests and compile Android**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.player.PlayerInteractionsTest :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual platform verification**

On Android, verify touch targets, Back, Home, next/previous, auto-next off/on, and skip boundaries. On Windows, verify keyboard controls, Back, Home, and VLC release/recreation after episode changes.

- [ ] **Step 7: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/player/PlayerScreen.kt composeApp/src/commonMain/kotlin/app/anim/App.kt composeApp/src/commonTest/kotlin/app/anim/player/PlayerInteractionsTest.kt
git commit -m "feat: complete player navigation controls"
```

### Task 5: Validate the player as a whole

**Files:**
- Modify: `docs/PLAYER-PREMIUM.md`

- [ ] **Step 1: Document current controls and marker coverage**

Update the player documentation to distinguish controls available for every video from skip actions requiring curated markers.

- [ ] **Step 2: Run player tests and both target compiles**

Run: `.\gradlew.bat :composeApp:desktopTest --tests 'app.anim.player.*Test' :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add docs/PLAYER-PREMIUM.md
git commit -m "docs: describe player navigation and skip markers"
```
