# SpaceAnime Android ads

Ads are Android-only banners on Home and the anime catalog. The Windows app, player, episode navigation, and full-screen/interstitial formats do not request ads. The app does not currently have a verified paid entitlement, so the placeholder plans do not disable ads.

## Remote switch

The backend exposes only the public flags at:

`https://spaceanime.onrender.com/api/app-config`

Apply `supabase/migrations/202611170004_runtime_ads_config.sql` to the development Supabase project first. Verify the default response is disabled:

```sql
select public.spaceanime_public_app_config();
```

To enable placements, update the row through the Supabase SQL editor or another trusted administrative channel:

```sql
update spaceanime_private.app_runtime_config
set ads_enabled = true,
    home_banner = true,
    catalog_banner = true,
    updated_at = now()
where singleton = true;
```

Set either placement flag to `false` to disable that location; set `ads_enabled = false` to turn off all ads. Do not grant client access to the private table and do not put `SUPABASE_SECRET_KEY` in the app. A successful response contains only `schemaVersion` and the three Boolean ad flags. A `503` means the backend could not read valid configuration; check that the migration is applied and the Render Supabase environment is configured.

Android starts with ads off when there is no valid cached configuration, refreshes the endpoint on app launch and every 15 minutes, and retains the last valid flags while offline. A remote switch therefore takes effect after a device next receives the updated configuration.

## AdMob and consent

1. Register the Android app in AdMob and create separate banner units for Home and catalog.
2. Configure any required privacy messages in AdMob's **Privacy & messaging** section. UMP checks consent information before Mobile Ads initializes or a banner is requested. If consent is denied, the form cannot be completed, or the consent refresh is unavailable, the app does not request ads.
3. Use the Android debug build to test. Debug uses Google's official sample AdMob app ID and banner unit IDs; never replace these with live IDs during development.
4. When UMP says a privacy-options entry point is required, **Opções de privacidade** is available below the banner.

For a signed Android release, set all three variables in the build environment:

- `ADMOB_APP_ID` — AdMob application ID, formatted `ca-app-pub-<16 digits>~<10 digits>`.
- `ADMOB_HOME_BANNER_ID` — Home banner unit ID, formatted `ca-app-pub-<16 digits>/<10 digits>`.
- `ADMOB_CATALOG_BANNER_ID` — catalog banner unit ID in the same format.

Release builds fail validation when any ID is missing or malformed. These AdMob identifiers are public identifiers, not Supabase or Firebase credentials. Do not add service-role keys or private credentials to Gradle, resources, or the APK.

## Safe rollout

Keep all flags off until the migration is applied and the public endpoint returns the expected flags. Validate with test ads first, then configure the release IDs and perform the consent flow on a test device. If a placement behaves unexpectedly, turn off its Supabase flag; devices apply the change after the next successful configuration refresh.
