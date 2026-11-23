# SDD ledger — plan: docs/superpowers/plans/2026-11-08-refatoracao-visual.md

Task 1: complete — HomeScreen.kt created; home UI composables extracted from App.kt.
Task 2: complete — App.kt keeps all state, stores, routes and API calls; only non-UI change is HomeNavFocus private→internal; imports cleaned.
Task 3: complete — quiet dark neutral surface ramp, 4px spacing rhythm, off-white primary CTAs, one violet accent, restrained typography.
Task 4: complete — compileKotlinDesktop → BUILD SUCCESSFUL; compileDebugKotlinAndroid → BUILD SUCCESSFUL; desktopTest → BUILD SUCCESSFUL.

Final review: subagent general (ses_e465e6374ffeu2ZJiyaJIa3Ipf). Critical: none.
Important fixed: muted text contrast #777182→#8F899F; HomeMeta over artwork changed from 6% white pill to 70% dark pill and 10sp→12sp.
Minor fixed: rail buttons 36dp→40dp; SpaceIcon glow opt-out for home arrows; poster focus/hover border; hero "Legendado" hue unified; status dot neutral before timestamp.
Final: minor (deferred): unreachable Trending branch in PosterRail items expression (pre-existing); unused onSearch parameter (pre-existing); 6/12dp nudges retained where optical alignment requires.

Ruling: kept `items = if (focus == HomeNavFocus.Trending) featured else featured.take(10)` unchanged because the enclosing condition makes the branch unreachable and the request was 100% logic preservation — cost if wrong: none (dead branch).
Ruling: added optional `glow` parameter to shared SpaceIcon with default true, so all other screens keep their existing behavior — cost if wrong: none (backward compatible).
Ruling: user asked to keep the purple identity — home CTAs now use `SpaceColors.primary`, borders/surfaces are purple-tinted, section headers have a solid purple bar and the hero uses `SpacePanelGradient` — cost if wrong: the home reads more branded/less neutral than the first unslop pass, which is what the user requested.
