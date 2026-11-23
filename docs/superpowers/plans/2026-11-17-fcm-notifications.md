# SpaceAnime FCM Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver opt-in Android FCM notifications for followed anime and manga, with a protected Render-triggered worker that collects releases and sends at most one digest per user per local day.

**Architecture:** Keep notification tokens and title subscriptions in private Supabase tables accessed only through authenticated backend routes and service-role RPCs. The Node backend collects supported release feeds, queues deduplicated digest items, claims due user digests transactionally, and sends through Firebase Admin; a Render Cron Job calls a secret-protected route on a schedule. Android uses Firebase Messaging, asks permission only after opt-in, and synchronizes only enabled categories, followed-title identifiers, and notification preferences.

**Tech Stack:** Node.js 22, `firebase-admin`, Supabase SQL/RPC, Render Web Service + Cron Job, Kotlin Multiplatform/Compose, Firebase Android BoM and Messaging SDK.

**Spec:** `docs/superpowers/specs/2026-11-16-spaceanime-discovery-library-player-notifications-design.md`

## Global Constraints

- Keep the existing Kotlin Multiplatform/Compose app, Android and Windows targets, and Supabase authentication.
- Sync only followed-title identifiers, source/audio variant, notification preferences, time zone, digest time, and device push token; never sync playback position, watch history, credentials, or the full local library.
- Android notifications are opt-in; request Android notification permission only after the user enables notifications.
- Scan supported feeds every six hours and send at most one followed-title digest per user per local calendar day at the user's chosen time; default to 20:00 in `America/Sao_Paulo`.
- Keep all Firebase Admin and Supabase service-role credentials server-side. Never commit or log private keys, push tokens, or authorization headers.
- Keep Windows push while the app is closed out of scope; preserve in-app Windows behavior.
- Do not claim production delivery until the new migration is applied and Render/Firebase are configured and smoke-tested.

## Review Focus

- Missing, malformed, or escaped Firebase private-key newlines must fail configuration explicitly; test both literal `\\n` and real newlines.
- Repeated/concurrent Cron requests must not send more than one digest per user/date; test competing claims and retry after an interrupted send.
- Invalid FCM tokens must be deleted without deleting other devices or a user's subscriptions; test mixed valid/invalid multicast results.
- Time-zone/DST boundaries must queue and deliver on the intended local date/time; test after-midnight and daylight-saving transitions.
- Permission denial, opt-out, sign-out, token refresh, and unauthenticated registration must not leave the UI claiming a subscription is active; test each platform boundary.

---

### Task 1: Private notification queue and subscription RPCs

**Files:**
- Create: `supabase/migrations/202611170002_notification_delivery.sql`
- Modify: `backend/notifications-store.js`
- Test: `backend/notifications-store.test.js`
- Modify: `backend/notifications-api.test.js`
- Modify: `backend/server.js`

**Interfaces:**
- `syncSubscriptions(userId, subscriptions)` replaces the authenticated user's allowed subscription set atomically.
- `listSubscriptions(mediaType, sourceId, after, limit)` pages only opted-in content IDs for a supported source; `claimScan(sourceId, now)` leases each source for six hours.
- `recordRelease({media_type, source_id, source_item_id, content_id, audio_variant, title, source_url})` stores a stable release and queues it only for matching subscriptions and enabled categories.
- `claimDueDigests(now, limit)` atomically claims due user/date digests and returns `{user_id, local_date, title, body, push_tokens}` rows.
- `finishDigest(userId, localDate, result)` records success/failure; `removeInvalidTokens(tokens)` removes only rejected FCM tokens.
- Add `PUT /api/v1/notifications/subscriptions` with ownership derived only from the verified bearer token.

- [ ] **Step 1: Write failing tests** for subscription ownership/allowlists, RPC payloads, release deduplication semantics, due digest claim inputs, and invalid-token cleanup.
- [ ] **Step 2: Run focused backend tests** and confirm they fail because the store and subscription route are missing.
- [ ] **Step 3: Add the migration RPCs** with `SECURITY DEFINER`, fixed `search_path`, service-role-only execution, row locking/lease state, idempotent release IDs, local-date scheduling, and closed client access.
- [ ] **Step 4: Implement the store and authenticated subscription route** using the interfaces above; reject malformed IDs, unsupported sources, oversized lists, and client-supplied user IDs.
- [ ] **Step 5: Run `node --test notifications-store.test.js notifications-api.test.js`**; all focused tests must pass.

### Task 2: Release collection and digest worker

**Files:**
- Create: `backend/notifications-collector.js`
- Create: `backend/notifications-collector.test.js`
- Create: `backend/notifications-fcm.js`
- Create: `backend/notifications-fcm.test.js`
- Create: `backend/notifications-worker.js`
- Create: `backend/notifications-worker.test.js`
- Modify: `backend/package.json`
- Modify: `backend/package-lock.json`
- Modify: `backend/server.js`

**Interfaces:**
- `collectReleases({ fetchImpl, store, now })` checks only the configured anime source hosts and MangaDex; isolated feed failures do not discard successful source results.
- `createFirebaseSender({ env, firebaseAdmin })` initializes Admin once using `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, and `FIREBASE_PRIVATE_KEY`; converts literal `\\n` to newlines and sends bounded FCM multicast batches.
- `runDailyDigest({ store, sender, now })` scans feeds on the six-hour cadence, claims only due digests, sends one per user/date, marks outcomes, and removes invalid registration tokens.
- `POST /api/workers/daily-digest` requires a constant-time validated `Authorization: Bearer <CRON_SECRET>`; missing configuration returns 503 and invalid credentials return 401.

- [ ] **Step 1: Write failing tests** for feed fixture parsing, per-provider failure isolation, stable release identity, private-key normalization, FCM batch limits, one digest per local date, concurrency/idempotency, and cron authentication.
- [ ] **Step 2: Run the focused tests** and confirm the expected missing-module/behavior failures.
- [ ] **Step 3: Add pinned `firebase-admin` and HTML parsing dependencies**; initialize Firebase lazily and explicitly reject incomplete credentials.
- [ ] **Step 4: Implement bounded feed collection** for the two existing anime sources and MangaDex, matching releases by exact source/content/audio identifiers and persisting only metadata required for delivery.
- [ ] **Step 5: Implement the worker and protected route**; send a concise notification payload, never expose tokens in responses/logs, and fail the request if the store or sender cannot complete its work.
- [ ] **Step 6: Run `node --test notifications-collector.test.js notifications-fcm.test.js notifications-worker.test.js notifications-api.test.js`**; all focused tests must pass.

### Task 3: Render scheduling and operational instructions

**Files:**
- Modify: `render-backend.yaml`
- Create: `backend/trigger-daily-digest.js`
- Modify: `backend/README.md`
- Test: `backend/trigger-daily-digest.test.js`

**Interfaces:**
- The Render Cron Job runs every 15 minutes and POSTs to the existing Web Service; the worker's database claim logic ensures delivery only at the user's digest time and only once per day.
- Render environment names are exactly `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY`, and `CRON_SECRET`; the Cron Job also receives `BACKEND_BASE_URL` and `CRON_SECRET`.

- [ ] **Step 1: Write failing trigger tests** for request URL, authorization header, timeout, and non-2xx handling without printing the secret.
- [ ] **Step 2: Implement the bounded HTTP trigger** using Node's built-in `fetch`; do not put credentials in the command line or logs.
- [ ] **Step 3: Add Web Service and Cron Job environment declarations** to the backend blueprint without changing the existing static update-feed service.
- [ ] **Step 4: Document migration application, Render Blueprint sync, Cron Job schedule, Firebase/Supabase variables, and smoke-test expectations.**
- [ ] **Step 5: Run `node --test trigger-daily-digest.test.js`** and validate the Render YAML parses with the repository's available tooling.

### Task 4: Android Firebase setup, consent, and registration

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Modify: `composeApp/src/androidMain/kotlin/app/anim/MainActivity.kt`
- Create: `composeApp/src/androidMain/kotlin/app/anim/notifications/SpaceFirebaseMessagingService.kt`
- Create: `composeApp/src/commonMain/kotlin/app/anim/notifications/NotificationPreferences.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/auth/AccountScreen.kt`
- Modify: `composeApp/src/jvmSharedMain/kotlin/app/anim/auth/AccountScreen.kt`
- Modify: `composeApp/src/jvmSharedMain/kotlin/app/anim/auth/AccountClient.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/profile/ProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/app/anim/App.kt`
- Test: `composeApp/src/desktopTest/kotlin/app/anim/notifications/NotificationPreferencesTest.kt`

**Interfaces:**
- Common models contain only the three category preferences and opaque subscription identifiers; no local playback/history models are added to network DTOs.
- `AccountState` gains authenticated suspend operations to register/update/remove the current device and synchronize allowed subscription identifiers; no access token is exposed to Compose callers.
- Android Compose obtains permission and the FCM token only after an explicit opt-in. Desktop receives a clear Android-only state and does not initialize Firebase.

- [ ] **Step 1: Write failing common tests** for default-off preferences, valid digest time/time zone payloads, and subscription projection that excludes progress/history.
- [ ] **Step 2: Add Google Services plugin 4.6.0 and Firebase Android BoM 35.2.0**; use `firebase-messaging` (the current main SDK artifact) rather than the obsolete KTX artifact, and ensure the existing module-root `google-services.json` is consumed only by Android.
- [ ] **Step 3: Implement typed common preferences and authenticated AccountClient methods** for device registration, preference changes, subscription replacement, token removal, and sign-out cleanup.
- [ ] **Step 4: Add the Android messaging service and manifest entry**; handle token refresh, foreground messages, and notification taps without logging registration tokens.
- [ ] **Step 5: Remove the launch-time permission prompt** from `MainActivity`; request permission only from the opt-in UI, persist consent/preferences locally, and register/update the device after permission and authentication succeed.
- [ ] **Step 6: Add the purple SpaceAnime preferences UI** in Profile Settings for anime, manga, announcements, and digest time; opt-out removes this device, and sign-out attempts server-side removal before clearing the session.
- [ ] **Step 7: Run `:composeApp:desktopTest`, `:composeApp:compileKotlinDesktop`, `:composeApp:compileDebugKotlinAndroid`, and `:composeApp:assembleDebug`**; confirm Android uses the supplied Firebase app configuration and Desktop remains buildable.

### Task 5: Final verification and handoff

**Files:**
- Review all files changed by Tasks 1–4; no keys, service-account JSON, or generated APKs are added to source control.

- [ ] **Step 1: Run `npm test`** in `backend`; all tests must pass.
- [ ] **Step 2: Run the desktop tests and Android debug assembly** from Task 4; all tasks must exit successfully.
- [ ] **Step 3: Verify the Supabase migration is present locally but not applied automatically**, and document that the owner must apply it before Render deploy.
- [ ] **Step 4: Report the exact deploy sequence and remaining external checks**; do not claim push is active until Render Cron, migration, Firebase Admin credentials, and a real Android device test are confirmed.
