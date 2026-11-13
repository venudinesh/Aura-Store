# Aura Store — the project in detail

A native Android client in the shape of the Google Play Store, backed by the **liteapks.com**
catalogue. Kotlin + Jetpack Compose (Material 3), no DI framework, no database: singletons,
`StateFlow` and `SharedPreferences`.

This is the orientation document. The technical reference is
[ARCHITECTURE.md](ARCHITECTURE.md), the feature ledger with its evidence is
[../README.md](../README.md), and the house rules for a change are in
[../CONTRIBUTING.md](../CONTRIBUTING.md).

---

## 1. What the app is, and what it deliberately is not

It is a **storefront and a pipeline**, not a browser wrapper:

* the catalogue's own module tree decides what every tab shows (rails, chips, orderings), so screens
  are server-defined rather than invented client-side;
* every artefact is streamed with a **byte range**, hashed with **SHA-256 while it streams**, parsed
  to read its package, version and signing certificate, then handed to Android's installer;
* a queue survives process death, pauses and resumes from the exact byte, and pops the panel that
  shows what it is doing;
* it never claims to install silently. A normal app cannot; the system sheet is the last step and the
  app is honest about it, including the case where the sheet is dismissed and nothing happened.

It does **not**: hold credentials, store a session, run its own update server, or scrape pages that
publish no direct link. It also cannot write `Android/obb` without root — it says so and exports the
correctly-named file instead.

---

## 2. Stack and build

| Piece | Value |
|---|---|
| Package / applicationId | `dev.aurastore.store` |
| Language | Kotlin, Compose (BOM-driven), Material 3 |
| Min / target SDK | 27 / 36 (two lint warnings, both waived: `OldTargetApi`, because the only handset is API 36, and
`PluralsCandidate` on `+%1$d more`, which English never inflects) |
| Toolchain | AGP 9.4.1, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00 |
| HTTP | OkHttp (no Retrofit — the catalogue's envelopes are read with `org.json`) |
| Images | Coil 3 |
| Background work | WorkManager (daily update check only) |
| Storage | `SharedPreferences`: `aurastore_settings`, `aurastore_installs`, `aurastore_updates` |

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.aurastore.store/.MainActivity
```

Unit tests: **295** in **41** classes under `app/src/test` (JVM only). Instrumented: 5 classes in `app/src/androidTest`,
which need a device — and `connectedDebugAndroidTest` drives *every* attached device, so it is not a
casual command.

---

## 3. Source map

```
app/src/main/java/dev/aurastore/store/
├── MainActivity.kt            the shell: brand bar, five tabs, route stack, exit dialog, swipe,
│                              floating download panel, profile sheet, about dialog
├── AuraStoreApp.kt              process start: settings, prefs, motion scale, queue restore
├── auto/AutoUpdateWorker.kt   daily update check + queueing under the Wi-Fi policy
├── data/
│   ├── Api.kt                 every catalogue endpoint, paging constants (LIST_PAGE_SIZE, LIST_MAX_PAGES)
│   ├── Models.kt              org.json models, WordPress entity decoding, playPackageId
│   ├── Hosts.kt               signed links (base64 epoch), referer, per-host refusal memory
│   ├── Settings.kt            theme, palette, auto-update policy, ignored updates, nav order, glass
│   ├── AppState.kt            postId -> install record
│   ├── InstalledApps.kt       device package list + remembered post/label/signer
│   ├── NavOrder.kt            the bar's permutation, sanitised and unit-tested
│   ├── ListPager.kt           one listing walked a page at a time
│   ├── SearchPager.kt         two search sources merged and deduped
│   └── sources/               the registry (LiteAPKs + six web catalogues), SourceHub,
│                              the shelf parsers: newest apps, categories, a category's apps
├── download/
│   ├── DownloadEngine.kt      the queue: states, pause/resume, verify, install handoff, notifications
│   ├── Transfer.kt            streaming + range resume + SHA-256, Android-free and unit-tested
│   ├── TransferRate.kt        smoothed bytes/s and ETA
│   ├── Failure.kt             typed failure: kind, one sentence, advice, worth-retrying
│   ├── QueueStore.kt          the queue as JSON rows in preferences
│   └── DownloadService.kt     the foreground service that keeps the process alive
├── install/
│   ├── Installer.kt           permission gate, pending install, intent + session installs
│   ├── InstallSessions.kt     PackageInstaller session callbacks
│   ├── ApkInspector.kt        package identity + signer digest
│   ├── Xapk.kt / Obb.kt       bundle recognition and unpacking; expansion-file pairing
│   ├── QuickInstall.kt        ABI-aware artefact choice
│   ├── Updates.kt             the two update rules (label vs label, strictly-newer)
│   ├── UpdateScanner.kt       6-hour cached verdicts per post + the pending badge
│   ├── PackageLookup.kt       installed package -> which listing is it (24 h cache, 8/pass budget)
│   └── AutoUpdater.kt         the one update flow behind the screen and the worker
├── ui/
│   ├── Components.kt          AppRow, AppCard, InstallButton, DownloadRow, states, paging footer
│   ├── NavBar.kt              the floating bar, its window, drag-reorder, glass rendering
│   ├── BarBackdrop.kt         the page sample behind the bar (PixelCopy + box blur)
│   ├── GlassShader.kt         the AGSL shader for the iOS 13+ engine
│   ├── PlayIcons.kt           every glyph, drawn as paths in unit space
│   ├── StoreMotion.kt         page transitions, shared icon, press feedback, entrances
│   ├── Route.kt / Screens.kt  the route stack and what it draws (both pure, both tested)
│   ├── TabSwipe.kt            the fraction/velocity rule behind a tab swipe (tested)
│   ├── StoreActions.kt        the one place a row's control decides what to do
│   ├── downloads/DownloadDock.kt  the animated floating panel
│   ├── source/SourcePicker.kt the sheet that asks which source a download comes from
│   ├── feed/SourceSections.kt the Browse tab's block per catalogue
│   └── feed, category, search, detail, you, settings, source, downloads  one folder per screen
└── theme/                     color, type, shape, motion tokens, Monet palette, glass maths
```

---

## 4. The catalogue (data layer)

Base: `https://liteapks.com/wp-json/v2/`. Every answer is `{status, message, data}`; `data` may be an
object or a bare array. The `User-Agent` must be `okhttp/3.12.13` — the endpoint's WAF rejects
others.

| Route | Used for |
|---|---|
| `GET v2/home` | sliders + tabs; each tab's `content` is a recursive **module tree** |
| `GET v2/posts` | paged list: `taxonomy`, `tax_ids`, `order_by`, `limit`, `page` |
| `GET v2/posts/{id}?abi=` | full detail incl. `versions[].version_downloads[]` |
| `GET v2/posts/recommended/{id}` | Similar apps |
| `GET v2/categories` | category tree |
| `GET v2/categories/{id}` | one term's own module tree + children + orderings |
| `GET v2/search?q=` | instant search — **cannot be paged** |
| `GET v2/hot_search` | trending chips |
| `GET v2/get_latest_version` | the app's own self-update probe |
| `GET wp/v2/posts?search=&per_page=&page=&_fields=` | the *same site's* WordPress REST API, for paging search |

Two walls were measured, not assumed:

* `v2/posts` answers pages 1–50 **whatever the `limit`**, so a listing's reach is `50 × limit`
  (`LIST_PAGE_SIZE` is 50, `LIST_REACH` 2 500). Page 51 returns `200` with an empty list, which is
  why the footer distinguishes *that is everything* from *the catalogue pages no further*.
* `v2/search` ignores `page` and `limit`, so search merges the ranked endpoint with `wp/v2/posts`,
  trims rows with `_fields` (~3.2 kB → ~150 B) and pages lazily as the list runs out.

### Signed links, and the 403

The catalogue's download page carries the file URL base64-encoded and appends a token minted in
`site.js`: `token = btoa(btoa(epoch_seconds + 3h))`. The CDN requires **token + `Referer`**; either
alone answers `403`, and the root path 403s too (which is why it once looked like a dead host).
`Hosts` reproduces the token, signs `*.liteapks.dev` and `*.9mod.space`, supplies the referer, and
remembers which hosts refuse so a refusal switches to another artefact of the same post instead of
retrying a link that will not work.

---

## 5. The download engine

### 5.1 States

```
QUEUED -> DOWNLOADING <-> PAUSED -> VERIFYING -> READY -> (INSTALLING) -> INSTALLED
                                             -> NEEDS_PERMISSION   (unknown sources off)
                                             -> AWAITING_CONFIRMATION (sheet open)
        any -> FAILED / CANCELED
```

`DownloadItem` carries the row's whole story: the URL and file name, byte counts, the digest, the
parsed package/version/signer, the bundle's pieces, a `failure`, a `notice`, and a `transitions`
history (capped at 24).

### 5.2 The transfer

`Transfer.stream` is deliberately Android-free so the interesting part — resuming — is testable
against a local HTTP server that speaks byte ranges:

* it re-reads and re-hashes the bytes already on disk **before** appending, so the SHA-256 always
  covers the whole file;
* a server that answers `200` to a range request gets a clean restart, because a half-written file
  plus a fresh stream would hash to something that never existed;
* `416` on a resume means the file is already complete — that is success, not a failure;
* `onCall` hands out the live call and `onResponse` the live response, so a pause can cancel the call
  **and** close the body the reader is blocked in. Both, because either alone has left a transfer
  running on the real handset;
* `onCancelled` is how a transfer learns it was stopped on purpose: it throws `PAUSED`, which the
  engine treats as a kept partial file rather than a network failure worth retrying.

### 5.3 Pause, precisely

`pause()` writes `PAUSED` **first**, then opens a new epoch (the generation counter that makes every
later write from that transfer refuse to touch the row), then closes the response body, cancels the
call, cancels the job, and settles the byte count from the file. `resume()` re-reads the file length,
and `run()` resumes from `target.length()` rather than from a tick — the target name carries the row
id, so the file is that row's own progress. The four defects this replaced are described in the
commit that introduced it.

### 5.4 Verification

Every file is hashed, then inspected: `ApkInspector` reads the package, version code/name and signing
certificate. A `.xapk`/`.apks` is a zip whose own `manifest.json` names the base package; its pieces
are unpacked beside the archive and the base gives the row its identity. A published digest, when a
source carries one, is compared before the row may become READY. A digest mismatch deletes
the file and its unpacked pieces.

### 5.5 Install hand-off

Two paths, tried in the order that survives OEM variance: the intent path (the platform installer,
targeted so an installed file manager does not offer an "Open with" chooser) is primary on ROMs that
abort a `PackageInstaller` session immediately; the session path is used for bundles, which the intent
cannot carry. A verified build is **not** handed over when the installed app's certificate differs —
that would be refused after the bytes were paid for — so the row says so and offers the removal
first. Confirmation is reported by a broadcast receiver, a session callback, and a 5-minute watchdog.
`Installer.rememberInstalled` writes the record that later makes the same app read *Open* and drives
the update list.

### 5.6 Bands, bytes and the cache

Files land in `cache/apk` named `postId-<rowid>-<name>` (query string and fragment dropped,
percent-decoded, capped at 80 characters with the extension kept — a signed URL otherwise produces an
`ENAMETOOLONG`). Anything in that directory no row names is swept when the queue goes idle; a row
deleted or cleared takes its archive **and** its unpacked pieces.

---

## 6. Updates

Two different questions, so two rules:

| Row kind | Compared by | Why |
|---|---|---|
| An app this store installed | build **label** vs build label | a mod APK reports the original app's `versionName`, so comparing versions is meaningless |
| Any other installed app | strictly-newer by version order, after a name lookup | the catalogue's `lastest_version` describes the original app, so the *file* side decides what can be delivered |

`AutoUpdater` owns the flow so the You screen, the badge and the background worker cannot disagree.
`UpdateScanner` caches a verdict per post for six hours; `PackageLookup` matches installed packages to
listings with a 24-hour cache and a per-pass budget. **Ignoring an update is per build**: the label is
stored, and a newer build brings the row back by itself. Ignored rows are listed under *Ignored
update* with *Show again*, and both the filter and the fetch honour the record, so the worker cannot
quietly download what was dismissed.

---

## 7. The shell and navigation

The shell is state-driven: a `tab` plus a **stack of encoded routes**. Routes are `Detail`,
`Download`, `Category`, `Settings`, `Source`; tabs are the floor, not history.

* `Route.kt` encodes each route to a string so the stack survives process death through
  `rememberSaveable`; an undecodable entry is dropped, which lands the user on the page below.
* Back, in order: close the profile sheet → close the about dialog → close the exit dialog → pop a
  route → return to the previously visited tab → Games → **exit dialog**. No gesture closes the app.
* Swiping between tabs follows the *display* order (`NavOrder`), commits at 18 % of the width or
  900 px/s (`TabSwipe`, unit-tested), and resists at the ends. The drag is a plain float so the page
  tracks the finger; only the settle and the tab transition are animated.

The bar itself is a separate **popup window** exactly the size of the pill, so a tap beside the
floating bar reaches the page. Its glass is a real material: translucency, a lit rim, grain, and a
sample of the page behind it read on the page's own pre-draw, box-blurred on a copy thread. Settings →
Navigation carries the switch, three engines (iOS 13+ shader, iOS 8+ drawn, Classic), blur, chromatic
split, refraction strength and depth, corner roundness, bar width, bottom offset, a reset, and the
drag-reorder row. The full port is [docs/NAVBAR_SETTINGS.md](NAVBAR_SETTINGS.md) and
[docs/PORTING_LIQUID_GLASS.md](PORTING_LIQUID_GLASS.md).

### The floating download panel

`ui/downloads/DownloadDock.kt`, drawn by the shell above the bar:

* it appears the moment a transfer is queued (`dockCandidate` prefers downloading → queued → paused →
  ready) and can be dismissed per row without touching the queue;
* the app's icon sits inside a **ring** that fills with the transfer, with a highlight travelling
  round it while bytes move;
* under the card, a **bar** fills on the same animated fraction in the store's two accents, with the
  same highlight crossing the filled part only;
* the percentage counts rather than jumping, and the status line morphs as the state changes;
* the one control morphs in place: **pause ⇄ resume**, and when the file verifies it becomes
  **Install** (or **Allow** when the install permission is missing), with the ring closing and a tick
  landing on the icon. The whole card is a way into that download's own page;
* a paused or finished row is still: the highlight only runs while bytes are moving.

---

## 8. Screens

| Tab / route | What it is |
|---|---|
| **Games**, **Apps** | the catalogue's own tab of that name: hero carousel, server rails, ordering chips, a paged flat list with an honest footer. The catalogue gives `App` a single module, so the Apps tab fills with four of its own categories — biggest first, deduped, read as rails through the same `taxonomy`/`tax_ids` query a module carries (`withAppFill`, `feed/Rails.kt`) |
| **Search** | every match for a term, paged in as the list runs out; hot searches; the *Download from* strip for other catalogues |
| **Browse** | the `Featured` tree plus the category picker; a term opens its own screen (rails, children, orderings) |
| **You** | Play's *Manage apps & device*: two views with their counts, one status line (`Checked 15:06`), update rows (with the three-dot menu), ignored list, download ledger, all-apps library with uninstall. It prints one footnote — `18 of 37 apps checked` or `24 apps aren't in the catalogue` — never a paragraph: the sweep's budget and the meaning of "up to date" are the code's business, not the reader's |
| **Detail** | the app's page: header with the shared icon flight, artefacts, screenshots, panels (About/Note/MOD info), similar apps, Play Store link, verification |
| **Download detail** | one row's page: rate, ETA, host, on-disk file, digest, transfer history, retry/pause, copy link |
| **Source** | another catalogue's own files, already resolved to direct URLs so the same pipeline runs |
| **Settings** | appearance (theme, palette, seed colour), navigation & glass, updates policy, sources, storage, about |

The account avatar opens the profile sheet: manage apps, downloads, install permission, settings,
about. The brand bar carries the active-download badge; the You tab carries the pending-update count.

---

## 9. Theme and motion

* Missing colour comes from the phone (Material You) as a *starting* value; choosing a colour overrules
  it for good. The palette engine works in CIE LCh(ab) with Material's tone numbers, and contrast is
  asserted across 29 seeds in both schemes.
* Every duration goes through `PlayMotion.ms`, which multiplies by the platform animation scale, so a
  phone that asked for no motion gets none anywhere.
* One movement per gesture: a committed swipe hands over to the tab transition; a tab change is a
  cross-fade with a short drift, while a pushed route slides.
* `playClickable` is press-scale + ripple over one interaction source; every drawn glyph that *is* a
  control carries a `label`, because a `Canvas` path is invisible to TalkBack.

---

## 10. Strings, accessibility, honesty

All user-facing text is in `res/values/strings.xml` and `StringsResourceTest` fails the build when an
entry is unreferenced, duplicated or untranslatable. The five classes of literal that stay in Kotlin
are the ones a string resource cannot hold, and each one carries a comment saying why. Every
control has a name
for a screen reader; failure messages name the thing that failed and the one action that can fix it.

Honesty rules the copy: a download that is not confirmed says so, an install the user cancelled is a
cancel rather than an error, a bundle is not called a broken APK, and a listing's reach is stated as
the number it actually got to.

---

## 11. Testing

| Kind | Where | Covers |
|---|---|---|
| JVM unit (338, in 44 classes) | `app/src/test` | transfer resume/digest, queue persistence, host credentials, paging walls, route/motion rules, palette contrast, glass maths, string hygiene, the shelf parsers of every source |
| Instrumented | `app/src/androidTest` | bar tap cells, nav inset, a rendered row's measured insets, `rememberSaveable` through activity recreation, OBB export |

The `.py` tools under `tools/` are project checkers: `audit_project.py` reads the whole tree for dead
code, stale numbers and orphaned evidence; `check_doc_links.py` proves every link in the docs still
resolves.

---

## 12. Where to go next

1. Walk the app on a handset: pause, ignore, back, swipe, and the new download panel. The
   instrumented tests in `src/androidTest` cover the parts of that walk a JVM test cannot reach.
2. Read [ARCHITECTURE.md](ARCHITECTURE.md) before changing a layer, and
   [../CONTRIBUTING.md](../CONTRIBUTING.md) before sending the change. The open list of what is
   still undecided lives with the maintainer rather than in this repository.
3. If you change the download pipeline, change `Transfer`'s tests with it — resume and digest are the
   two things this app cannot get wrong quietly.
