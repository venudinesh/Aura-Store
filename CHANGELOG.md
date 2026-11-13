# Changelog

All notable changes to Aura Store. The format follows [Keep a Changelog](https://keepachangelog.com/),
and the project uses `MAJOR.MINOR.PATCH` versions.

## [Unreleased]

### Added

- **The files a project needs to be joined.** `CONTRIBUTING.md` says how a change is built, checked
  and sent, and what the two unusual gates in this repository are for. `SECURITY.md` says how to
  report something that should not be public, and what the release signing key is worth if it
  leaks. A code of conduct, two issue templates and a pull request template are in place.
- **The README says plainly** that the project is a client for the catalogues it reads and is not
  affiliated with them.
- **A user guide.** `docs/USER_GUIDE.md` covers install, the five tabs, the source sheet, updates,
  games with OBB data, storage, privacy, and the fixes for the failures a person meets.
- **The debug build ships with the release.** The release page carries
  `Aura-Store-v1.1.0-debug.apk` beside the signed APK, each with its `.sha256`.
  `tools/publish_release.py` uploads all four, and `release-notes/v1.1.0.md` says which build to
  install.

### Changed

- **Search results are tabs, not a stack.** Each site that answers gets a tab with its match count,
  and one site's rows are on screen at a time. The rows no longer repeat the site in a pill; the tab
  already names it. The catalogue tab still pages, and its count grows as pages arrive.
- **The release is signed on the machine that holds the key.** The GitHub Actions workflow that
  published it is gone. It needed the release keystore, the store password and the key password to
  sit in the repository's secret store, and a workflow that holds a signing key is a key that a
  mistake can hand over. Releasing is now six steps on the release machine, described in
  `docs/RELEASING.md`, and the key never leaves it. The build, the tests, the lint and the two
  gates still run on every push.
- The README now says what the app does with root, on a phone that grants it: one thing, placing
  an app's data folder where the system expects it.
- **The agent's own files are not published.** `AGENTS.md` and `.agents/` stay on the machine. The
  prose checker they held is now `tools/anti-ai-slop/`, where the gate calls it, and the CI step
  points at the new path.
- **The design exploration is out of the tree.** `logo/explore/` held 63 working files behind the
  launcher art. The master icon, the mark and the two rasters the README and the app use stay; the
  exploration stays on the machine.
- **The documents quote the build they ship.** 338 tests, versionCode 2, and the 2.46 MB release
  against the 15.66 MB debug build.

### Fixed

- **No key of a catalogue's is in the repository.** The pages the parsers are tested against are
  copies of real pages, and a real page can carry a key: a Firebase key, a push key, a site
  verification token, a session token, a captcha secret, a pre-signed storage link with its own
  signature, a comment widget's nonce. Each one is now a placeholder in the fixture, and a sweep
  runs over every tracked file on the build, so a key cannot be pushed into the published tree
  without the build saying so.
- **The Gradle distribution is pinned by digest.** The wrapper now carries the checksum of the
  archive it downloads, so a clone cannot be taught to run a tampered one.
- **The `.gitignore` covers more than the keystore.** It names the other shapes a secret takes:
  a base64 copy of a key, the token file of a service, a registry login a command line reads
  without asking, a private key outside a keystore, a Terraform state, and what a handset hands
  back, which is the queue with its signed links, a database, a trace and a log.

## [1.1.0] - 2026-11-12

### Added

- **Browse carries every source.** Each enabled catalogue gets its own block in the Browse tab: the
  apps it published last, and the categories it groups them under. A source's name opens that
  source's own page, where its newest apps and its categories can be read on their own.
- **A download asks where it comes from.** Tapping Install opens a sheet that lists every enabled
  source, says which of them has the app and which do not, resolves each answer to a direct file
  link, and downloads from the source that is picked. The catalogue's own build is in the list with
  the other sites.
- **A default source.** Settings has a *Default download source* row. The sheet opens on that source,
  already expanded, and the word *Default* sits beside its name.
- **Search asks every source.** One term goes to the catalogue and to the other sites at once. The
  catalogue's own rows come first, and the other sites' answers follow in a group of their own, each
  row carrying the site it came from as a small pill beside the file size.
- **A term finds its subject even when it is typed as one word.** `spiderman` is asked again with
  the word split, and then without its trailing words, before a source's answer is called empty. A
  source's own page puts those results above its shelf, and the shelf returns when the field is
  cleared.
- **Shelf parsers for every source.** The catalogue reads its own API, 9MOD its WordPress namespace,
  5play the home page's *New* shelves, GetModsAPK the sitemaps it publishes, AN1 its *Latest games*
  carousel and its genre menu, ApkVision its `/updated/` page and its chip rows.

### Fixed

- Game links that carry a literal space in the path (`…/Subway Surfers/…apk`) download. The token
  was appended to the raw string and only then encoded by the HTTP client, so the link that was
  signed and the link that was requested were different text. Every URL is now encoded before it is
  signed, exactly once, and an existing `%XX` escape is left alone.
- A storage authority whose name no longer resolves (`gp.liteapks.com`) is remembered as dead, like
  a host that refused, and the same path is fetched from the sibling that serves it
  (`gp4.liteapks.com`). Third-party buckets are not rewritten, because they have no sibling.
- The download dock's ring is lit at the byte the transfer has reached, pulses only while bytes
  move, and lands with one expanding ring when the file completes. The card's wash carries sparks
  along its edge on the same phase, so the icon and the card share one light source.

### Changed

- The app's version is 1.1.0. The application id and the signing key are unchanged, so this release
  updates an installed copy in place and keeps its data.
- **A search row downloads from the source it shows.** The pill already names the place, so the row
  no longer asks again: one file goes straight into the queue, and several open the sheet narrowed
  to that site, where the pick is the file, not the place. The app page and the default download
  still ask.
- **9MOD rows carry their icons and download again.** The search route names a post but not its
  picture, so the adapter asks the posts route for `featured_media` and the media route for
  `source_url`. The site now puts direct `gp4.9mod.com` anchors on the download page, and those are
  read before the old nonce and per-file pages.
- **UpdateMe is gone**, with its enum, adapter, string, fixture and the *seven sources* counts in
  the docs.
- The project is MIT licensed. The full text is in `LICENSE`. The typeface stays under the SIL Open
  Font License, and the two sit together without a conflict.

## [1.0.0] - 2026-11-03

The first release under this name: a Play-Store-shaped client for the Liteapks catalogue, with a
verified download and install pipeline rather than a browser wrapper.

### Changed

- The project is Aura Store. The application id is `dev.aurastore.store`, and every Kotlin package,
  class, resource, signing property and release secret follows that name. The signing key is a new
  certificate issued under it.
- The launcher icon is the badge now: a ring-lit amber disc filling the tile, with the white A and
  its cream counter. `logo/icon-1024.png` is the 1:1 master, and `tools/render_logo.py` cuts the
  adaptive foreground, the themed vector and the ten legacy rasters from the same drawing, so a
  change lands everywhere at once.

### Documentation

- A prose gate scores every maintained document against the
  [anti-ai-slop](https://github.com/misbahsy/anti-ai-slop) rules. `tools/prose_gate.py` keeps a
  recorded floor per document and runs in CI.
- The handoff note and the pause analysis were rewritten to clear that gate.

### Catalogue

- Home, categories, app detail, search, hot searches and recommendations from the catalogue API.
- Paged search across both the ranked and the WordPress routes, with counts that name what has
  arrived and what is still coming.
- Paged listings and rails with a footer that distinguishes *more coming*, *failed, retry* and
  *that is everything*.
- Multiple catalogues behind **Download from**: LiteAPKs, 9MOD, 5play, GetModsAPK, AN1 and
  ApkVision.

### Downloads and installs

- Queue that survives process death, with pause, resume, cancel and retry.
- Range-resumed transfers, streaming SHA-256 verification, speed and ETA.
- Typed failures with one honest sentence and one action each.
- APK, `.xapk` and `.apks` support; the archive's own manifest chooses the base package.
- Installer hand-off, unknown-sources gate, package and signature inspection, publisher-clash
  protection, and the system's own uninstall sheet.
- OBB data files located, renamed to Android's rule and placed or exported.
- Update detection for this store's installs and for installed apps matched back to the catalogue.
- Optional background update checks on a Wi-Fi / any-network / off policy.

### Interface

- Play-Store-shaped shell, cards, rows, detail page and motion tokens.
- Five-tab navigation bar, reorderable by long-press drag.
- Liquid-glass navigation material: enable switch, three engines (iOS 13+ shader, iOS 8+ drawn,
  Classic), blur, chromatic split, refraction strength and depth, corner roundness, bar width and
  bottom offset, with reset.
- Light, dark and system themes; dynamic and user-seeded palettes.
- Download detail page with progress, host, verification, history and copy-link.
- The split install button draws both halves in the same fill, so the smaller chooser no longer reads
  as the heavier control.

### Verification

- 297 JVM tests across 42 classes, plus 18 instrumented tests.
- Unit-tested catalogue parsing, pagination, queue persistence, transfer, failures, package
  identity, archive unpacking, update rules and glass mappings.
- Device passes on a Redmi Note 8 Pro (Android 16 / API 36), recorded in `verify_redmi/`.

### Security

- Every HTTP client refuses a URL that is not `https`, including a redirect hop, so a source cannot
  downgrade a download to cleartext. The manifest already forbids cleartext traffic.
- Android backup is off, so the download queue and the signed links inside it stay on the device.

### Known limits

- No TalkBack on the test ROM, so screen-reader traversal is unverified.
- The pre-API-33 shader fallback is unit-tested, not device-tested.
- The OBB root branch has no device with `su` to exercise it.
- R8 and resource shrinking are on for release: the APK is 2.38 MB against the debug build's 15.38 MB,
  and the shrunk artefact is installed and smoke-tested on the phone (feed drawn, Search raised the IME).
