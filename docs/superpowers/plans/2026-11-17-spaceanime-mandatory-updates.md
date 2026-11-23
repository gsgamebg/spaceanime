# SpaceAnime Mandatory Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep a known mandatory release blocking old app builds through restarts and network outages while allowing users in when no requirement has ever been received and Render is unavailable.

**Architecture:** Preserve the existing Render manifest and download redirect. Add a small platform-specific local store for the highest required release and load it before the app proceeds past its startup update gate.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, OkHttp, Kotlin serialization, Android SharedPreferences, Windows local file storage, Node backend update-feed.

**Spec:** [2026-11-17-spaceanime-player-updates-ads-design.md](../specs/2026-11-17-spaceanime-player-updates-ads-design.md)

## Global Constraints

- A higher published build remains mandatory, as in the current client behavior.
- Keep a previously learned requirement across app restart and failed/regressing manifest responses.
- If no requirement is cached and Render cannot be reached, allow entry and retry later.
- Never install APKs silently; Android must present its system installation confirmation.
- Continue publishing both Android APK and Windows ZIP for each release because the current backend requires both.
- Versions predating the update checker require one manual bootstrap update.

## Review Focus

- Render unreachable on first launch: no unknown update may be fabricated; app opens.
- Cached requirement higher than installed build: app remains blocked after restart/offline.
- Cached requirement equal to or below installed build: stale gate is cleared.
- Malformed/corrupt local state: reject it safely without treating corrupt data as a valid release.
- Local persistence failure: keep the current-session gate active and surface a clear error; do not silently continue.
- Manifest regression or server error: neither clears nor lowers a previously stored requirement.

---

### Task 1: Define required-release persistence behavior

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/anim/update/RequiredReleaseStore.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/update/UpdatesTest.kt`

**Interfaces:**
- Produces `interface RequiredReleaseStore { suspend fun load(): AppRelease?; suspend fun save(release: AppRelease); suspend fun clear() }`.
- Produces `requiredReleaseForStartup(installedCode: Int, stored: AppRelease?): AppRelease?`.
- Produces `updateRequirementAfterCheck(installedCode: Int, stored: AppRelease?, checked: AppRelease?): AppRelease?`.
- Produces `@Composable expect fun rememberRequiredReleaseStore(): RequiredReleaseStore`.

- [ ] **Step 1: Write failing pure behavior tests**

Add `startupGateUsesOnlyAStoredHigherBuild` and `newRequirementsNeverRegress`, covering no cached value, equal/lower code, higher code, and failed/regressing checks.

- [ ] **Step 2: Run tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.UpdatesTest`
Expected: compilation failure for the missing store/gate API.

- [ ] **Step 3: Add the store contract and pure gate helper**

Use the existing `AppRelease` fields; keep only requirements whose `code > APP_VERSION_CODE`. Keep `retainHighestRelease` monotonic.

- [ ] **Step 4: Run tests and verify they pass**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.UpdatesTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/update/RequiredReleaseStore.kt composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt composeApp/src/desktopTest/kotlin/app/anim/update/UpdatesTest.kt
git commit -m "test: define persistent mandatory update gate"
```

### Task 2: Implement Android and Windows required-release stores

**Files:**
- Create: `composeApp/src/androidMain/kotlin/app/anim/update/AndroidRequiredReleaseStore.kt`
- Create: `composeApp/src/desktopMain/kotlin/app/anim/update/DesktopRequiredReleaseStore.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/update/DesktopRequiredReleaseStoreTest.kt`

**Interfaces:**
- Android actual uses app-scoped `SharedPreferences` and synchronous `commit()` before showing a newly required release.
- Windows actual stores a bounded JSON record under the current user's SpaceAnime application data directory and writes atomically.
- Both actual implementations satisfy `rememberRequiredReleaseStore()`.

- [ ] **Step 1: Write failing Windows persistence tests**

Add `requiredReleaseSurvivesReopening`, `corruptOrOversizedStateIsRejected`, `clearRemovesTheStoredRequirement`, and `saveFailureIsReported`.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.DesktopRequiredReleaseStoreTest`
Expected: compilation failure for the missing desktop implementation.

- [ ] **Step 3: Implement bounded Windows persistence**

Serialize only the release fields required by `MandatoryUpdateScreen`; cap the file at 8 KiB, parse strictly, and use an atomic replacement with the existing file-store pattern.

- [ ] **Step 4: Implement Android persistence**

Store the same validated release fields in app-scoped `SharedPreferences`; do not store credentials or personal data.

- [ ] **Step 5: Run persistence tests and compile Android**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.DesktopRequiredReleaseStoreTest :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/androidMain/kotlin/app/anim/update/AndroidRequiredReleaseStore.kt composeApp/src/desktopMain/kotlin/app/anim/update/DesktopRequiredReleaseStore.kt composeApp/src/desktopTest/kotlin/app/anim/update/DesktopRequiredReleaseStoreTest.kt
git commit -m "feat: persist required releases across launches"
```

### Task 3: Apply the cached gate before app navigation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/anim/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/update/UpdatesTest.kt`

**Interfaces:**
- `App` loads the store before clearing `initialUpdateCheck`.
- A valid higher manifest release is merged with the cached release and persisted before rendering the mandatory screen.

- [ ] **Step 1: Add failing startup/cache integration tests**

Test `updateRequirementAfterCheck` with null, lower, equal, and higher manifest codes. A null/lower result must preserve a stored higher requirement; a stored requirement at or below the installed code must clear.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.UpdatesTest`
Expected: failure for the unimplemented cache transition behavior.

- [ ] **Step 3: Load and clear cached state at startup**

In `App`, obtain the store through `rememberRequiredReleaseStore()` before normal navigation. If the stored release is higher than `APP_VERSION_CODE`, display the mandatory screen immediately; if equal/lower, clear the stale value and continue checking the online manifest. Surface store read/clear errors instead of treating them as successful persistence.

- [ ] **Step 4: Persist a newly announced release**

After validating a successful manifest, call `updateRequirementAfterCheck`, set the in-memory gate, then save the resulting higher requirement before normal navigation. If saving fails, keep the current-session gate active and show a storage error. A network failure must not clear the stored value. Keep the existing six-hour retry cadence.

- [ ] **Step 5: Run update tests and compile Android**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.update.UpdatesTest :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/App.kt composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt composeApp/src/desktopTest/kotlin/app/anim/update/UpdatesTest.kt
git commit -m "feat: keep known mandatory updates enforced offline"
```

### Task 4: Document and verify the release bootstrap

**Files:**
- Modify: `docs/ATUALIZACOES.md`
- Test: `backend/update-feed.test.js`

- [ ] **Step 1: Add backend assertions for the current release contract**

Verify an incomplete release (missing Android APK, Windows ZIP, or valid SHA-256) remains unavailable and that approved GitHub/Supabase artifact URLs are still redirected by Render.

- [ ] **Step 2: Run backend update-feed tests**

Run: `npm test --prefix backend -- --test-name-pattern="feed|artifact"`
Expected: all selected update-feed tests pass.

- [ ] **Step 3: Document the mandatory behavior and one-time bootstrap**

Explain that any higher manifest build blocks after a successful check, cached requirements remain blocking offline, first-check failure permits entry, Android installation requires confirmation, and pre-checker versions must be manually updated once. Document that both platform artifacts are required.

- [ ] **Step 4: Commit**

```powershell
git add backend/update-feed.test.js docs/ATUALIZACOES.md
git commit -m "docs: clarify mandatory update rollout"
```

