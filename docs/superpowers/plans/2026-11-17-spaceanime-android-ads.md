# SpaceAnime Android Ads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add remotely controlled, consent-aware AdMob banners on selected Android navigation pages without interrupting playback.

**Architecture:** Store only public ad-enable/placement flags in Supabase; expose them through a small Render endpoint. Android reads and caches the flags, initializes Google Mobile Ads only after consent, and renders banners through a platform-specific Compose implementation; Windows remains ad-free.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Google Mobile Ads SDK, Google UMP SDK, Node.js backend, Supabase PostgreSQL/RPC.

**Spec:** [2026-11-17-spaceanime-player-updates-ads-design.md](../specs/2026-11-17-spaceanime-player-updates-ads-design.md)

## Global Constraints

- Ads are Android-only and limited to Home and catalog screens.
- No ads appear in the player, over playback controls, between episodes, or as full-screen/interstitial ads.
- No valid remote configuration means ads are off; refresh configuration on launch and every 15 minutes.
- Keep only the last valid public configuration in cache; do not cache ad responses or consent as if consent were granted.
- Do not request or render ads until the consent SDK permits it.
- Supabase service-role/secret credentials never enter the APK; only the backend reads protected configuration.
- The existing plan cards are not entitlements. Do not claim or charge for an ad-free benefit in this change.

## Review Focus

- Missing, malformed, or oversized config: app remains ad-free and server returns an explicit unavailable response.
- Remote kill switch: an enabled cached flag cannot override a newer valid disabled config.
- Offline after valid config: use the last valid flag; when no cache exists, remain ad-free.
- Consent denied or required form not completed: do not initialize/request an ad.
- Wrong platform/placement: desktop and player render no ad view even if a remote flag is enabled.

---

### Task 1: Add a private runtime-ad configuration and public read endpoint

**Files:**
- Create: `supabase/migrations/202611170004_runtime_ads_config.sql`
- Create: `backend/runtime-config-store.js`
- Create: `backend/runtime-config.test.js`
- Modify: `backend/server.js`

**Interfaces:**
- Produces `GET /api/app-config` returning `{ schemaVersion: 1, ads: { enabled: Boolean, homeBanner: Boolean, catalogBanner: Boolean } }`.
- `createRuntimeConfigStore(env, fetchImpl).read()` returns a validated configuration.
- `createHandler` accepts an injectable `runtimeConfigStore`.
- Supabase RPC is `public.spaceanime_public_app_config()`; the only table is `spaceanime_private.app_runtime_config`.

- [ ] **Step 1: Write failing store and route tests**

Add tests named `runtime config returns only public ad flags`, `runtime config rejects malformed remote config`, `app config route returns validated config`, and `app config route reports unavailable store`. Assert that the response never contains environment keys or private data.

- [ ] **Step 2: Run tests and verify they fail**

Run: `npm test --prefix backend -- --test-name-pattern="app config|runtime config"`
Expected: tests fail because the store and endpoint are not implemented.

- [ ] **Step 3: Add the configuration table and read RPC**

Create a singleton row with ads disabled by default and separate Home/catalog booleans. Keep it in `spaceanime_private`, deny direct `anon`/`authenticated` access, and expose only a bounded JSON result through a public read RPC. Admin edits happen through the Supabase dashboard/service role.

- [ ] **Step 4: Implement the backend store and route**

Use the existing `SUPABASE_URL` and `SUPABASE_SECRET_KEY` only on the backend. Validate exact booleans/schema, bound the response body to 4 KiB, use HTTPS with redirect disabled, and return a clear 503 on storage failure.

- [ ] **Step 5: Run backend tests**

Run: `npm test --prefix backend -- --test-name-pattern="app config|runtime config"`
Expected: all selected Node tests pass (exit code 0).

- [ ] **Step 6: Apply migration in development Supabase**

Verify the defaults are disabled, the public RPC reveals only the three flags, and direct table reads/writes are denied to `anon` and `authenticated`.

- [ ] **Step 7: Commit**

```powershell
git add supabase/migrations/202611170004_runtime_ads_config.sql backend/runtime-config-store.js backend/runtime-config.test.js backend/server.js
git commit -m "feat: expose safe remote ad configuration"
```

### Task 2: Add cross-platform ad slots and Android config cache

**Files:**
- Create: `composeApp/src/commonMain/kotlin/app/anim/ads/Ads.kt`
- Create: `composeApp/src/jvmSharedMain/kotlin/app/anim/ads/AdsConfigClient.kt`
- Create: `composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdBanner.kt`
- Create: `composeApp/src/desktopMain/kotlin/app/anim/ads/DesktopAdBanner.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/HomeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/sources/SourcesScreen.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/ads/AdsConfigTest.kt`

**Interfaces:**
- Produces `enum class AdPlacement { Home, Catalog }`.
- Produces `data class AdsConfiguration(val enabled: Boolean = false, val homeBanner: Boolean = false, val catalogBanner: Boolean = false)`.
- Produces `parseAdsConfiguration(text: String): AdsConfiguration?` and `AdsConfigClient.fetch(): AdsConfiguration?`.
- Produces `expect @Composable fun AdBannerSlot(placement: AdPlacement)`.
- Android actual fetches `/api/app-config` on launch and every 15 minutes; desktop actual renders nothing.

- [ ] **Step 1: Write failing config parser tests**

Add tests for valid flags, missing fields, wrong JSON types, unsupported schema version, and oversized payload returning no enabled configuration.

- [ ] **Step 2: Run the tests and verify they fail**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.ads.AdsConfigTest`
Expected: compilation failure for the missing parser/model.

- [ ] **Step 3: Implement a strict shared parser and config client**

Use the existing OkHttp/JSON dependencies. Require HTTPS to the configured Render host, reject redirects and responses over 4 KiB, and expose only the validated flags.

- [ ] **Step 4: Implement safe cache and platform slots**

Android starts disabled, uses a valid cached response while offline, refreshes on launch and every 15 minutes, and replaces the cache only after a valid response. Desktop actual is a no-op.

- [ ] **Step 5: Place slots on Home and catalog**

Add a reserved banner slot to Home and the anime catalog only. Keep it out of `PlayerScreen`, profile settings, and episode navigation.

- [ ] **Step 6: Run parser tests and both target compiles**

Run: `.\gradlew.bat :composeApp:desktopTest --tests app.anim.ads.AdsConfigTest :composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/ads/Ads.kt composeApp/src/jvmSharedMain/kotlin/app/anim/ads/AdsConfigClient.kt composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdBanner.kt composeApp/src/desktopMain/kotlin/app/anim/ads/DesktopAdBanner.kt composeApp/src/commonMain/kotlin/app/anim/HomeScreen.kt composeApp/src/commonMain/kotlin/app/anim/sources/SourcesScreen.kt composeApp/src/desktopTest/kotlin/app/anim/ads/AdsConfigTest.kt
git commit -m "feat: add remotely controlled Android ad slots"
```

### Task 3: Integrate AdMob and consent

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Create: `composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdsConsent.kt`
- Modify: `composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdBanner.kt`
- Create: `docs/AD-MONETIZATION.md`

**Interfaces:**
- Android banner implementation consumes a valid remote `AdPlacement` flag and an affirmative UMP consent state.
- Debug builds use Google's official test app/ad unit IDs; production builds require the user's real AdMob app ID and Home/catalog ad unit IDs.

- [ ] **Step 1: Add the Google Mobile Ads and UMP dependencies**

Add version-catalog entries and Android-only dependencies; do not add either SDK to common or desktop source sets.

- [ ] **Step 2: Implement consent-before-request behavior**

Use UMP to refresh consent information and show a required form before initializing/loading an ad. Consent denied, unavailable, or incomplete means no ad request.

- [ ] **Step 3: Render native Android banners**

Use `AndroidView` for AdMob `AdView`; map only Home/Catalog to their configured unit IDs. Do not include an interstitial format or a player placement.

- [ ] **Step 4: Configure manifest IDs safely**

Use the official Google test application ID in debug. Require a production application ID and real unit IDs for a release build; do not put Supabase service-role or other private keys in Gradle or the APK.

- [ ] **Step 5: Document operational setup**

Document Supabase flag editing, Render endpoint verification, AdMob account/app/unit setup, consent review, kill switch behavior, and the fact that current placeholder plan cards do not suppress ads.

- [ ] **Step 6: Run tests and build checks**

Run: `npm test --prefix backend`; then `.\gradlew.bat :composeApp:desktopTest :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug`.
Expected: all tests pass, APK builds with test IDs, and desktop builds without an ad SDK.

- [ ] **Step 7: Manual test with test ads**

Verify Home/catalog flags on/off, config failure, offline cache, consent accepted/denied, Android rotation/recomposition, desktop no-op, and no ad view during playback. Do not use live ads during development tests.

- [ ] **Step 8: Commit**

```powershell
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/androidMain/AndroidManifest.xml composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdsConsent.kt composeApp/src/androidMain/kotlin/app/anim/ads/AndroidAdBanner.kt docs/AD-MONETIZATION.md
git commit -m "feat: integrate consent-aware AdMob banners"
```

### Task 4: Prepare the coordinated release

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt`
- Modify: `composeApp/build.gradle.kts`
- Create: `distribution/release-0.7.22.json`

- [ ] **Step 1: Set the coordinated release to 0.7.22 / code 38**

Use one code for Android APK and Windows ZIP so the current Render manifest accepts the release.

- [ ] **Step 2: Validate**

Run: `npm test --prefix backend` and `.\gradlew.bat :composeApp:desktopTest :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug :composeApp:packageReleaseWindowsZip`.
Expected: all tests and builds pass; Android debug uses test ad IDs; Windows package contains no ad SDK.

- [ ] **Step 3: Stage, do not publish**

Calculate artifact hashes and verify both public download URLs before changing Render's `SPACEANIME_RELEASE_JSON`. Enable ad flags in Supabase only after the test build has been validated.

- [ ] **Step 4: Commit**

```powershell
git add composeApp/src/commonMain/kotlin/app/anim/update/Updates.kt composeApp/build.gradle.kts distribution/release-0.7.22.json
git commit -m "release: prepare SpaceAnime 0.7.22"
```
