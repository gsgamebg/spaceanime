# SpaceAnime discovery, library, player, updates, and notifications design

## Intent

Make the existing Android and Windows SpaceAnime apps useful from the moment a
user opens a catalog destination: show browsable anime and manga, load more real
source results, avoid repeated release cards, and make playback continuation
part of the user's list. Improve episode navigation in the player and turn the
existing update feed into a release process that can enforce supported versions.
Add opt-in Android push notifications for followed-title updates and important
announcements, without hourly notification spam.

The user selected separate destinations (A), and a single library with
**Tudo / Continuar / Favoritos** filters (A). The user authorized syncing only
followed-title identifiers and notification preferences for personalized
notifications. Playback position and history must remain local.

## Current state and constraints

- The app is Kotlin Multiplatform with Compose UI in `commonMain`, JVM-shared
  source clients, Android-specific Media3, and Windows/JVM-specific libVLC.
  Keep both deliverables; do not rewrite the app or replace its navigation.
- Selecting **Explorar** currently opens a blank search screen. `SourcesScreen`
  only performs search; `SourceRepository.searchAll` has no page parameter.
  Existing genre/trending functions have source-specific limitations.
- MangaDex and Yomu are queried only after a manga search. Manga results and
  chapter pages already have pagination, but the screen has no initial catalog,
  save/follow action, or chapter-progress store.
- Recent episodes from both anime servers are interleaved. The current key uses
  the display title, episode label, and part of the server URL; it is not a
  stable cross-server identity and can leave duplicate releases.
- `LibraryStore` persists anime favorites and playback progress on-device with
  atomic replacement. It distinguishes favorites by source, content ID, and
  dubbed state. `LibraryScreen` renders continuation and favorites as separate
  sections.
- `PlayerScreen` already supports playback, seek, volume, speed, tracks,
  subtitles, retry, and resume. It does not own an episode queue or navigate to
  the previous/next episode.
- A mandatory-update screen and six-hour manifest check already exist. The app
  opens downloads externally; it does not silently install updates or validate
  the downloaded file itself. A public release feed and stable Android signing
  key are operational prerequisites. Current checked-in release instructions
  describe this setup, but APK/EXE publication and end-to-end rollout are not
  established by a local build.
- Android notifications currently cover playback and an inactivity reminder.
  There is no FCM integration, push-token registration, title-subscription API,
  announcement delivery service, or notification scheduler. The backend has
  Supabase authentication and a release manifest, but is not yet a production
  notification service. The user's manga list and anime playback progress are
  currently local.
- External catalog providers can be incomplete or unavailable. Each screen
  must retain partial results, expose retry/error/empty states, and avoid
  claiming that a provider is online when it has not been checked.

## Goals

1. **Explorar has content without a query.** Present real browse sections
   (available/trending and categories) and a searchable catalog. Keep search
   and browse states distinct and preserve source attribution.
2. **Load more from providers.** Add source-aware page requests for search and
   browse where supported. "Próxima" must fetch another provider page, not just
   slice an already loaded list. Retain the current results on partial failures
   and stop when the provider reports exhaustion.
3. **Manga has a home catalog.** On entry, show manga cards and useful
   browse/search actions before a query. Use only the existing MangaDex/Yomu
   adapters and their supported public endpoints; identify the source on each
   result, show partial availability, and paginate provider data.
4. **Deduplicate release cards without hiding choices.** Group equivalent
   episodes across servers using a strict key of normalized displayed series
   title, parsed episode number, and audio version. Normalize case, Unicode
   diacritics, punctuation, and whitespace; do not use fuzzy title matching or
   source URL fragments. A card appears once, but opening it retains the
   available server choices; do not conflate dubbed and subtitled editions.
   When a title cannot be normalized or an episode number cannot be parsed,
   keep the source item separate rather than risking a false merge.
5. **Make Minha Lista coherent.** Provide the selected filters:
   - **Tudo:** union of anime favorites, followed manga, and unfinished
     playback items, with duplicate records in the union removed by their
     existing stable IDs.
   - **Continuar:** unfinished anime playback entries with resume position.
   - **Favoritos:** saved anime plus followed manga.
   Preserve source/audio identity and existing local playback data. Following
   or unfollowing a manga must be available from manga results/details.
   Completed, non-favorite anime remain in History, not in Continuar.
6. **Improve player navigation.** Provide previous/next episode actions when
   the source has adjacent episodes, keep the current server/source context,
   show an explicit next-episode action at the end, and preserve resume
   behavior. Do not auto-play the next episode by default.
7. **Make updates operationally safer.** Preserve the manifest/update screen,
   verify release metadata and artifact hashes before publishing, keep
   Android signing continuity, and document the per-platform install step.
   Continue checking at launch and on the existing six-hour interval. An
   unavailable manifest must not bypass a mandatory block already shown in
   that session. Do not claim that a client-side block is server-side
   enforcement or that an external download is automatically verified.
8. **Add opt-in Android push.** Offer separate preferences for followed anime
   episodes, followed manga chapters, and general announcements. Ask for
   notification permission only after the user enables notifications. Use
   Android push when the app is closed; show announcements and required-update
   status in the Windows app while it is running.
9. **Protect the user's data.** For personalized push, sync only opaque
   provider/content identifiers, required source/audio variant, notification
   preferences, time zone, and device push token. Do not sync playback
   position, watched history, credentials, or full local library. Provide
   opt-out and remove server subscriptions/tokens on unsubscribe or sign-out.

## Notification policy proposal

- Scan providers in a scheduled backend worker every six hours, retaining
  independent cursors and tolerating a provider outage.
- Send at most one followed-title digest per user per local calendar day, at
  the user's chosen time; default to 20:00 in `America/Sao_Paulo`. A digest
  may contain both anime and manga entries. Do not send an alert for every
  provider scrape or every hour.
- Send explicitly marked important service announcements immediately, with
  server-side deduplication and an expiry. Ordinary announcements may be
  included with the daily digest.
- Keep release-mandated update blocking separate from notification delivery.
  An update announcement can be sent as a communication, but the installed
  app's version check remains the enforcement mechanism.
- Android is the only out-of-app push target in this scope. Windows receives
  in-app announcements/update status; no promise of notifications after the
  Windows app closes.

These intervals and the default delivery time are implementation defaults
chosen to limit noise; notification preferences must let the user opt out or
change the digest time.

## Approaches considered

1. **Incrementally improve the current Kotlin/Compose app and connect the
   existing Supabase/Render services — recommended and selected.** Preserve the
   navigation destinations and source adapters; add provider pagination,
   manga browse/follow, stable release grouping, filtered list, and player
   episode navigation. Add a private push-subscription store, an Android FCM
   client, a scheduled backend collector/digest sender, and release validation.
   This directly addresses the reported behavior and builds on existing
   authentication and release infrastructure. It requires external Firebase
   project configuration and a backend deployment before production push is
   live.
2. **Unify or rewrite catalog navigation and data models.** This could give
   anime/manga a uniform catalog abstraction, but is a larger regression risk
   across Android and Windows and is unnecessary for displaying content on
   existing destinations. Rejected for this iteration.
3. **Patch only the screens and send local notifications.** This is quicker
   but cannot deliver notifications while the app is closed, cannot target
   followed titles, and cannot fix actual remote pagination. Rejected because
   it does not meet the stated outcomes.

## Data flow and implementation boundaries

- Keep Compose screens in `commonMain`; add typed repository operations and
  provider-specific implementations in the existing source layers. Use a
  provider page result with `items`, `hasMore`, and per-provider errors rather
  than guessing a page count.
- Keep the MangaDex/Yomu data model separate from anime episodes. Use
  MangaDex's supported catalog ordering for the initial manga browse screen.
  Treat Yomu as search-only unless its public API's browse contract is
  confirmed by a fixture and a bounded live check; do not synthesize popular
  results from arbitrary search terms. Introduce a local manga-follow store
  rather than changing the anime-library JSON schema. The combined UI derives
  **Tudo** and **Favoritos** from the existing anime store and manga-follow
  store without moving anime progress.
- Represent grouped recent releases as one logical episode with one or more
  playback candidates. Keep the candidate's provider ID, URL resolution, and
  audio version intact. A failed candidate must allow the user to select a
  different available provider.
- Provide the player with an episode sequence and callbacks from the source
  browser/library flow. Navigation loads the selected episode through the same
  source resolver, then creates a fresh player session and persists only local
  resume state.
- Keep the update manifest's public contract backward-compatible. A
  schema-valid manifest blocks the client only when its `versionCode` is
  higher than the installed Android version; Windows compares its supported
  release code using the existing platform contract. Missing/invalid
  manifests do not create a new block, but a mandatory block already shown is
  not removed by a later network failure in that session. Add release checks
  to scripts or tests; publishing artifacts, configuring secrets, deploying
  Render/Firebase, or signing releases requires the user's external project
  credentials and is not implied by a local code change.
- Authenticate notification registration and preference changes with the
  existing Supabase user session. Store device tokens and subscriptions in
  private server-managed Supabase tables with RLS closed to direct client
  access; validate ownership at the backend and use server credentials only
  in Render environment configuration.
- Send notifications through Firebase Cloud Messaging (Android). Add a
  scheduled backend worker that checks the supported source feeds, records
  stable release identifiers, queues digest items, and enforces a unique
  per-user/per-day send. Expire invalid device tokens and isolate provider
  errors. A protected server-side path may publish announcements; an
  administrator UI is not part of this iteration.
- If Firebase project credentials, Render scheduled jobs, or Supabase schema
  access are not available, implement and test the code paths locally but
  leave production delivery explicitly unconfigured. Never add keys or tokens
  to the repository or ask the user to paste secrets into chat.

## Non-goals

- Replacing Kotlin/Compose, reworking the entire visual identity, or merging
  every catalog destination into one page.
- Syncing the complete anime library, playback positions, history, or manga
  reading progress between devices.
- Windows push while the application is closed; desktop self-updating or
  Android silent installation; cryptographically signed manifests in this
  iteration.
- Auto-play of the next episode, downloads, DRM, payment/entitlement changes,
  or execution/installation of third-party extensions.
- An announcement CMS/admin dashboard or automatic cross-provider title
  matching without a stable identity.
- Hourly user-facing notifications.

## Success criteria and verification

- Unit tests prove that source pagination requests subsequent pages, preserves
  partial results, handles exhaustion, and does not invent pages when a source
  fails. UI-facing state includes loading, error/retry, empty, and populated
  cases.
- Fixture tests prove that the same episode served by both anime providers
  appears once only when normalized title, parsed episode number, and audio
  version match; both playable candidates remain available. Different titles,
  unparsed episodes, and dubbed/subtitled releases remain separate.
- Manga tests prove initial catalog content without a search query, pagination,
  follow/unfollow persistence, and isolated source failures.
- Library tests prove **Tudo / Continuar / Favoritos** membership, stable
  favorite identity, resume actions, and that corrupt local files are still
  preserved.
- Player tests prove previous/next boundaries, session replacement, resume
  persistence, explicit end-of-episode next action, and no default auto-play.
- Backend tests prove authentication/ownership for subscriptions, no
  notification without consent, token removal, provider failure isolation,
  digest de-duplication and one-per-day maximum, time-zone scheduling,
  announcement authorization/expiry, and that secrets do not enter manifests
  or client responses.
- Update tests prove only a higher valid release blocks the client, the
  published APK/Windows archive match the manifest hashes and approved HTTPS
  origins, and a release retains Android signing continuity. Real rollout is
  verified only after the user publishes files and configures the public
  services.
- Run the focused desktop tests and Android debug assembly that cover changes.
  A successful build is not evidence of physical-device playback, FCM
  delivery, or public deployment; report these separately.

## Decisions and calls to confirm

- **User selections:** Separate destinations (A); `Minha Lista` filters (A);
  Android push plus Windows in-app communications; personalized follow
  notifications are authorized to sync only followed-title IDs and
  preferences, not playback history.
- **Builder calls:** Keep the existing Kotlin/Compose architecture; retain
  local anime playback history; add manga follow without introducing manga
  reading progress; use strict (not fuzzy) cross-server release grouping;
  prefer MangaDex for first-load manga browse until Yomu browse is verified;
  daily digest at the user's local chosen time, default 20:00
  America/Sao_Paulo; source scan every six hours; urgent announcements
  immediate; no auto-play or Windows closed-app push.
- **Operational prerequisites:** Firebase Android project and server-side
  credentials, Supabase migration/service configuration, Render scheduled
  worker deployment, public APK/Windows artifacts, and the same Android
  signing key used for the installed release. These must be configured by the
  project owner outside the source tree.
