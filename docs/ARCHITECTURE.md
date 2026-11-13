# Aura Store — architecture and reference

This is the technical reference for the whole app: what runs where, why each layer is shaped the way
it is, and which invariants the code is not allowed to break. The orientation document is
[PROJECT_GUIDE.md](PROJECT_GUIDE.md), the feature summary is [../README.md](../README.md), and the
house rules for a change are in [../CONTRIBUTING.md](../CONTRIBUTING.md).

Read this when you need to change something and want to know what else depends on it.

---

## 1. What the app is

A Play-Store-shaped storefront for the **liteapks.com** catalogue, with a download and install
pipeline that verifies what it fetches. It is one Android app, one process, no backend of its own:

| | |
|---|---|
| Package | `dev.aurastore.store` |
| Language | Kotlin, Jetpack Compose, Material 3 |
| Min / target / compile SDK | 27 / 36 / 37 |
| HTTP | OkHttp only (the catalogue speaks JSON that is read with `org.json`) |
| Images | Coil 3 |
| Persistence | `SharedPreferences` — `aurastore_settings`, `aurastore_installs`, `aurastore_updates`, plus the queue record |
| Background | WorkManager, one daily update check |
| DI / database | none, by choice: singletons, `StateFlow`, preferences |

It deliberately does not hold a session, scrape pages without a direct link, install silently (the
platform forbids it), or write `Android/obb` without root — the last one is exported through
`MediaStore` instead, and the row says so.

---

## 2. Layer map

```
                    ┌────────────────────────────────────────────┐
                    │ MainActivity — the shell                   │
                    │ brand bar · 5 tabs · route stack · swipe    │
                    │ download dock · floating bar window         │
                    └───────────────┬────────────────────────────┘
                                    │ UiState + callbacks
       ┌────────────────────────────┼──────────────────────────────┐
       │                            │                              │
┌──────▼──────┐            ┌────────▼────────┐            ┌────────▼────────┐
│ ui/ screens │            │ ui/StoreActions │            │ download/       │
│ feed search │            │ one place a     │            │ DownloadEngine  │
│ detail you  │            │ row's control   │            │ Transfer        │
│ settings …  │            │ decides         │            │ QueueStore      │
└──────┬──────┘            └────────┬────────┘            └────────┬────────┘
       │                            │                              │
       └────────────┬───────────────┴──────────────┬───────────────┘
                    │                              │
            ┌───────▼────────┐            ┌────────▼────────┐
            │ data/          │            │ install/        │
            │ Api Hosts      │            │ Installer       │
            │ Models paging  │            │ ApkInspector    │
            │ Settings       │            │ Xapk Obb        │
            │ sources/       │            │ Updates Loops   │
            └───────┬────────┘            └────────┬────────┘
                    │                              │
                    └──────────► platform ◄────────┘
                        (OkHttp, PackageManager, PackageInstaller,
                         SharedPreferences, MediaStore, WorkManager)
```

Nothing above the bottom row holds a `Context` except the singletons that are initialised with one
(`AuraStoreApp`), and everything that makes decisions about bytes (`Transfer`, `QueueStore`,
`ListPager`, `NavOrder`, `TabSwipe`, `Route`, the glass maths) is Android-free so it can be tested on
the JVM.

The source picker sits in the shell, beside the profile sheet and the exit dialog, because every
screen has to be able to open it: an app page, a source page, the Browse tab, the search results.
It is the one modal that is not about the app's own state.

---

## 3. Data layer

### 3.1 The catalogue

Base URL `https://liteapks.com/wp-json/v2/`. Every answer is `{status, message, data}`; `data` may be
an object **or** a bare array. The `User-Agent` must be `okhttp/3.12.13` — the endpoint's WAF rejects
anything else.

| Route | Shape | Used by |
|---|---|---|
| `v2/home` | sliders + tabs, each tab's `content` a recursive module tree | feed, rails |
| `v2/posts` | `taxonomy`, `tax_ids`, `order_by`, `limit`, `page` | listings, charts, categories |
| `v2/posts/{id}?abi=` | post detail with `versions[].version_downloads[]` | detail screen |
| `v2/posts/recommended/{id}` | similar apps | detail screen |
| `v2/categories` | term tree with `count` | Browse, category picker |
| `v2/categories/{id}` | one term's module tree, children, orderings | category screen |
| `v2/search?q=` | instant search — **cannot be paged** (ignores `page`, `limit`, `offset`) | search seed block |
| `v2/hot_search` | trending chips | search |
| `v2/get_latest_version` | the app's own self-update probe | about/settings |
| `wp/v2/posts` | the same site's WordPress REST API — paged, `_fields`-trimmable | search pages |

Three measured walls, not assumptions:

1. **`v2/posts` answers pages 1–50 only**, whatever the `limit`. Page 51 is `200` with an empty list.
   Reach is `50 × limit`; `LIST_PAGE_SIZE` is 50, so `LIST_REACH` is 2 500 rows.
2. **`v2/search` ignores paging.** Search therefore merges the ranked route with `wp/v2/posts`,
   dedupes by id, and pages lazily.
3. **`wp/v2/posts` has no page window**: it answers up to `X-WP-TotalPages` and then a `400`
   `rest_post_invalid_page_number`, so a search walk has a real end.

`Api` turns a null `data` into an empty list on a **list** route (an empty category is a real answer)
and throws *Post N is not in the catalogue* on a **post** route.

### 3.2 Signed links

The catalogue's download page carries the file URL base64-encoded plus a token minted in `site.js`:

```
token = base64(base64(epoch_seconds + 3h))
```

The CDN needs **both** the `?token=` and a `Referer` of the site. Either alone is `403`, and so is
the root path — which is why the host once looked dead. `Hosts` reproduces the token, signs
`*.liteapks.dev` and `*.9mod.space`, supplies the referer, and remembers hosts that refused, so a
refusal switches to another artefact of the same post instead of retrying a dead link.

### 3.3 Paging

Two pure walkers, both unit-tested:

* `ListPager` — one ordering, a page at a time, until a short page, a page that adds nothing, or the
  measured ceiling. `stoppedAtCeiling` is what lets the footer distinguish *that is everything* from
  *the catalogue pages no further*, and it prints the reach it stopped at.
* `SearchPager` — ranked seed block + `wp/v2` pages, deduped, with a real last page.

A listing has no length on the wire (no `X-WP-Total`), so a bottom of a list may say *loading*,
*the server's own refusal*, or *that is everything* — never a percentage of a total that does not
exist.

### 3.4 The other catalogues

`data/sources/` is a registry of six catalogues: LiteAPKs plus five web ones (9MOD, 5play,
GetModsAPK, AN1, ApkVision). Each adapter resolves its own pages to direct URLs, so the
**same** download pipeline runs for every one of them, and any digest a source publishes is
verified before the row may become `READY`.

Each source answers three questions about itself, and the interface says so:

| Member | Question |
|---|---|
| `search(term)` | which apps does this site have for that word |
| `files(app)` | which direct file links does this app have here |
| `latest()` | what did this site publish last |
| `categories()` | how does this site group what it published |
| `appsIn(category)` | which apps are in one of those groups |

`SourceHub` is the one place they are asked: it keeps a section per source for half an hour, asks
every enabled source for a term at once, and hands the answers to the screens. A source that cannot
be read is an empty section that says so, not a hole in the page.

The Browse tab draws one block per source: the source's name, its categories, and the apps it
published last. The sheet that asks where a download comes from opens the same registry, and so does
the search, which asks every source the same term and groups the answers by site.

---

## 4. Download engine

### 4.1 States

```
QUEUED -> DOWNLOADING <-> PAUSED -> VERIFYING -> READY -> INSTALLING -> INSTALLED
                                            -> NEEDS_PERMISSION     (unknown sources off)
                                            -> AWAITING_CONFIRMATION (intent sheet open)
        any -> FAILED / CANCELED
```

`DownloadItem` carries the row's whole story: url, file name and its `postId-rowid-name` target,
byte counts, digest, parsed package/version/signer, bundle pieces, `failure`, `notice`, and a
`transitions` history capped at 24.

### 4.2 The epoch rule

Every transfer runs inside a generation counter. `pause()` (and `cancel`, and `remove`) opens a new
epoch, and every later write from that transfer checks its epoch and refuses to touch the row. This
is what stops a progress tick or a `catch` block from un-pausing a row after the user stopped it.

**`pause()` writes `PAUSED` first**, then opens the epoch, then closes the response body, cancels the
call, cancels the job, and finally settles the byte count from the file. The order is the fix for the
report "pause did not pause": every step after the write is asynchronous, so a state written last
loses the race with the transfer's own catch.

### 4.3 The transfer

`Transfer.stream` is Android-free, so resuming is testable against a local range-speaking server:

* re-reads and re-hashes what is already on disk **before** appending, so the SHA-256 always covers
  the whole file;
* a server answering `200` to a range request gets a clean restart — half a file plus a fresh stream
  would hash to something that never existed;
* `416` on resume means complete: success, not failure;
* `onCall` hands out the live call and `onResponse` the live response, so a pause can cancel the call
  **and** close the body the reader is blocked in — either alone has left a transfer running on the
  real handset;
* `onCancelled` is how a transfer learns it was stopped on purpose: it throws `PAUSED`, kept as a
  partial file rather than a network failure.

Resume trusts the **file**, not the row: `run()` passes `resumeFrom = target.length()`, and the target
name carries the row id, so whatever is in it is that row's progress.

### 4.4 Verification

Hash first, then inspect: `ApkInspector` reads package, version code/name and signing certificate. A
`.xapk`/`.apks` is a zip whose `manifest.json` names the base package; pieces are unpacked beside the
archive and the base gives the row its identity. A published digest is compared before `READY`. A
mismatch deletes the file and its pieces. The split-APK path is a `PackageInstaller` session; a single
APK prefers the intent path, which survives more OEM installers.

### 4.5 Install hand-off

Order matters and is deliberate: a verified build is **not** handed over when the installed app's
certificate differs — the platform would refuse after the bytes were paid for — so the row says what
the removal costs and offers the system's own uninstall sheet first. Confirmation is reported by a
broadcast receiver, a `PackageInstaller` session callback, and a five-minute watchdog; the intent path
is the one a normal app cannot observe, and the copy says so.

### 4.6 Queue persistence and the cache

`QueueStore` writes the queue as JSON rows; `restore()` merges them, reattaches to files on disk and
re-audits installed packages. Files land in `cache/apk` as `postId-<rowid>-<name>` (query and
fragment dropped, percent-decoded, capped at 80 characters keeping the extension — a signed URL
otherwise produces `ENAMETOOLONG`). `pruneIfIdle` sweeps anything no row names the moment nothing is
moving; a removed row takes its archive and its unpacked pieces with it.

---

## 5. Updates

Two different questions, two rules:

| Row kind | Compared by | Why |
|---|---|---|
| An app this store installed | build **label** vs build label | a mod APK reports the original app's `versionName`, so version order is meaningless |
| Any other installed app | strictly newer by version order, after a name lookup | the catalogue's `lastest_version` describes the original app, so the **file** side decides what can be delivered |

`AutoUpdater` owns the flow so the You screen, the badge and the worker cannot disagree.
`UpdateScanner` caches a verdict per post for six hours; `PackageLookup` matches installed packages to
listings with a 24-hour cache and a per-pass budget (8 packages), and distinguishes *not in the
catalogue* from *not asked yet*.

**Ignoring an update is per build, not per app**: the build label is stored, and a newer build brings
the row back by itself. Both the filter and the fetch honour the record, so the background worker
cannot quietly download a dismissed build.

---

## 6. The UI shell

### 6.1 State

The shell is `tab` + a **stack of encoded routes** (`Route.kt`), so it survives process death through
`rememberSaveable`; an undecodable entry is dropped and the user lands on the page below. Tabs are the
floor, not history.

### 6.2 Back

```
profile sheet open?  -> close it
about dialog open?   -> close it
exit dialog open?    -> close it
a route is pushed?   -> pop it
a tab was visited?   -> go back to it
not on Games?        -> Games
otherwise            -> the exit dialog
```

Nothing a gesture does can close the app; leaving is a deliberate tap, and the dialog says that
queued downloads keep running because they are a foreground service.

### 6.3 Tab swipe

The offset is a plain `mutableFloatStateOf` set synchronously on every pointer event, so the page
tracks the finger; `animateFloatAsState` with a `snap()` spec is used while the finger is down, and
only the settle is animated. A commit hands the movement straight to the tab transition (one movement
per gesture); a non-commit springs back. `TabSwipe` (pure) decides at 18 % of the width or 900 px/s.

### 6.4 Vertical budget: the bar, the dock, and the rule between them

Two surfaces float over page content, and both are reserved through **one** composition local,
`LocalNavBarInset`, which every list adds to its bottom padding:

```
bar inset  = FloatingBar.inset(floating, heightFor(fontScale), offset)   // 68 dp at 1.0×
page inset = FloatingBar.reserve(fontScale, barShown, floating, offset, dockHeight)
           = (barShown ? bar inset : 0) + (dockHeight ? dockHeight + 8 dp : 0)
```

`FloatingBar` in [../app/src/main/java/dev/aurastore/store/ui/NavBar.kt](../app/src/main/java/dev/aurastore/store/ui/NavBar.kt)
is the only place the bar's geometry is defined, because the sheet, the sampled rectangle, the tap
window and every list must agree:

* `heightFor(fontScale)` grows by exactly what the label line gains, so the sheet keeps 10 dp of air
  at both ends at any text size;
* `inset(floating, height, offset)` is what a page reserves; the gap it floats on is
  `glassBottomGapDp(offset)` = 0…24 dp, and only when the bar is in its floating form;
* `reserve(fontScale, barShown, floating, offset, dockHeight)` is the whole band: the bar's share
  plus the panel's measured height plus the 8 dp `dockGap`, and each surface is counted only while
  it is actually up. `dockHeight` is `null` when the panel is hidden, so the page does not reserve a
  card that is not there. This function is the page's only source for its bottom padding.

The panel does not place itself at all. It is a child of the bar's own `Popup`, in one `Column`
whose children are the panel, an 8 dp `Spacer`, and the sheet. The gap is therefore layout rather
than arithmetic: no font scale, bar shape, width fraction or offset can make the two meet. The band
opens and closes with a fade and a vertical expand/shrink from the bottom, so the sheet stays pinned
while the panel arrives above it. On a page that hides the bar the panel is the bottom surface and
floats on the same 8 dp it would have kept above the sheet.

The bar itself is a **`Popup` window exactly the size of the pill** — anything larger would paint
nothing and eat taps — and its gap is the popup's own offset, not padding inside the window. It is
hidden while the IME is visible (a child window sits above the keyboard and would eat its bottom row)
and while a modal sheet or dialog is up (its window draws above a modal).

### 6.5 The download panel

`ui/downloads/DownloadDock.kt`, drawn by the shell above the bar:

* it appears the moment a row is queued, and follows one row: downloading/verifying → queued →
  paused → ready (dismissing it is per row and never touches the queue);
* the app's icon sits beside the card's progress wash that fills with the transfer on the same
  animated fraction with one highlight crossing it. When ready to install, a completion badge lands
  a tick on the icon. The card's wash gradients live across the whole width and are clipped rather
  than rebuilt per frame;
* the percentage counts rather than jumping, and the status line morphs on `DownloadState` — never on
  the formatted sentence, whose percentage changes several times a second and would restart the
  crossfade forever;
* the single control morphs in place: pause ⇄ resume, then Install (or Allow when the install
  permission is missing);
* the whole card opens that download's own page.

### 6.6 Glass

The floating bar's material is real translucency, a lit rim, grain, and a **sample of the page behind
it**: `PixelCopy` of the bar's own rectangle, taken on the page's pre-draw so it follows a scroll
rather than a timer, box-blurred on a copy thread. Three engines: iOS 13+ (AGSL `RuntimeShader`
refraction), iOS 8+ (drawn magnification), Classic (flat surface). Blur, refraction, chromatic split,
depth, corner, width and bottom offset are preferences; the full port is
[NAVBAR_SETTINGS.md](NAVBAR_SETTINGS.md) and [PORTING_LIQUID_GLASS.md](PORTING_LIQUID_GLASS.md).

---

## 7. Theme, motion, copy

* Missing colour comes from the phone (Material You) as a **starting** value; choosing a colour
  overrules it permanently. The palette engine works in CIE LCh(ab) with Material's tone numbers, and
  contrast is asserted across 29 seeds in both schemes.
* Every duration goes through `PlayMotion.ms`, which multiplies by the platform animation scale, so a
  phone that asked for no motion gets none anywhere.
* One movement per gesture; tab↔tab is a cross-fade with a short drift, pushed routes slide.
* `playClickable` is press-scale + ripple over one interaction source.
* Every user-facing sentence is in `res/values/strings.xml`, and `StringsResourceTest` fails the build
  when an entry is unreferenced, duplicated or untranslatable. The five classes of literal that stay in
  Kotlin are argued in CONTINUATION §3r.
* Every control has a name for a screen reader — a drawn glyph that *is* a control carries a `label`,
  because a `Canvas` path is invisible to TalkBack.
* The copy is honest by rule: an unconfirmed install says it is unconfirmed, a cancelled install is a
  cancel rather than an error, a bundle is not called a broken APK, and a list states the reach it
  actually got to.

---

## 8. Testing

| Kind | Where | Covers |
|---|---|---|
| JVM unit - **338 tests in 44 classes** | `app/src/test` | transfer resume/digest, queue persistence, host credentials, paging walls, route/motion rules, palette contrast, glass maths, string hygiene, the shelf parsers of every source |
| Instrumented — **18 tests in 5 classes** | `app/src/androidTest` | bar tap cells, nav inset, a rendered row's measured insets, `rememberSaveable` through activity recreation, OBB export |

`connectedDebugAndroidTest` drives **every** attached device and can clear app data; it is not a
casual command. The JVM suite is the gate:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Project checkers (stdlib Python, run from the root):

* `tools/audit_project.py` — dead code, unreferenced resources, stale numbers the docs quote, test
  integrity, build config, screenshot naming.
* `tools/check_doc_links.py` — every relative link resolves, and a `#LNNN` anchor still names what its
  label says.
* `tools/dump_ui.py` — a `uiautomator` dump helper.

---

## 9. Build and release

| Piece | Value |
|---|---|
| AGP / Gradle / Kotlin | 9.4.1 / 9.8.2 / 2.4.21 |
| Compose BOM | 2026.09.00 |
| versionCode / versionName | 2 / 1.1.0 |
| R8 | on (`isMinifyEnabled = true`, `isShrinkResources = true`): 2.46 MB against the debug build's 15.66 MB, with the enum and worker keep rules in `app/proguard-rules.pro`; validated by installing the release APK |
| Signing | local keystore, gitignored; `docs/RELEASING.md` documents the four secrets and the tag flow |

`targetSdk` is 36 while `compileSdk` is 37. Lint's one remaining warning is `OldTargetApi`; it is
waived because the only handset available runs API 36 and raising the target opts into behaviour
changes that would then ship untested.

---

## 10. Invariants

These are the rules the code is built around. Breaking one is a bug even if the suite is green.

1. **Resume must never lose bytes.** `Transfer` resumes only when the handed count equals the file
   length; anything that changes one without the other turns a resume into a silent restart from zero.
2. **`pause()` writes the state first** and must never call `call.response()` — it throws while the
   call is executing, which is exactly when pause is tapped.
3. **Every write from a transfer checks its epoch.** A stopped row is stopped for every path.
4. **One geometry, one stack.** Bar height, gap and the page's bottom reserve come from
   `FloatingBar.reserve`; the panel and the sheet are two children of one `Column`, not two
   placements that have to agree. A second formula anywhere is how the dock ended up inside the bar
   in landscape once already.
5. **The panel never enters the bar's rectangle** — not at rest, and not while it animates.
6. **A row that owns bytes is the only thing that can delete them,** and `pruneIfIdle` deletes what no
   row owns.
7. **Nothing claims more than was observed**: an install is confirmed by a broadcast, a session
   callback or the watchdog — never by a package merely existing.
8. **The digest covers the whole file**, always re-read from disk before an append.
9. **No string is written in a composable.** Resources or it does not ship.
10. **The catalogue's own numbers are never invented.** Reach, totals and ceilings are printed as what
    the routes actually answered.

---

## 11. Where to look for what

| Question | File |
|---|---|
| an endpoint, a paging constant | `data/Api.kt` |
| why a link needed a token | `data/Hosts.kt` |
| the queue's states and transitions | `download/DownloadEngine.kt` |
| resuming, hashing, ranges | `download/Transfer.kt` |
| the queue on disk | `download/QueueStore.kt` |
| install hand-off, permissions, clashes | `install/Installer.kt` |
| package/version/signer | `install/ApkInspector.kt` |
| bundle recognition | `install/Xapk.kt`, `install/Obb.kt`, `install/ObbInstaller.kt` |
| update rules | `install/Updates.kt`, `install/AutoUpdater.kt` |
| what a row's button does | `ui/StoreActions.kt` |
| bar geometry, tabs, dock stacking | `ui/NavBar.kt` |
| the page sample behind the bar | `ui/BarBackdrop.kt` |
| the shader | `ui/GlassShader.kt` |
| routes and back | `ui/Route.kt`, `ui/Screens.kt`, `MainActivity.kt` |
| the floating panel | `ui/downloads/DownloadDock.kt` |
| motion tokens | `theme/Motion.kt`, `ui/StoreMotion.kt` |
