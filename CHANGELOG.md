# Changelog

All notable changes to Aura Store. The format follows [Keep a Changelog](https://keepachangelog.com/),
and the project uses `MAJOR.MINOR.PATCH` versions.

## [1.8.0] - 2026-11-19

### Added

- **A new app page.** The catalogue's banner fills the top, the app's mark sits over its lower edge,
  and the three facts that identify the build share a bordered card. The install card carries the
  live transfer strip while bytes move, the speed, and the build chooser behind a chevron. About,
  MOD info, release notes and the app's facts are carded the same way.
- **A new source picker.** The sheet opens on the app's own art, then one row of sources. Each chip
  says *Found* with its version, *Default* when Settings names it, looks for the app, or says it is
  not there. The open source answers with resolved files: size, build, published digest, and a
  Download button that puts the file in the same queue as the catalogue.
- **One notification for the whole queue.** A download asks for notifications the first time it
  starts, so a refusal at launch no longer leaves progress silent forever. One notification follows
  every transfer: the title counts the packages, the line names the app whose bytes are moving, and
  the bar covers the queue's total. Updates ride the quiet channel and alert once.
- **Search answers while a term is typed.** A pause of 300 ms previews the catalogue's instant
  matches. The Search key still walks every enabled source.

### Changed

- **A list keeps its place.** View-all rails and category pages hand their scroll state back to the
  shell, so closing a detail returns to the row that opened it. Each tab keeps its page under a
  detail too.
- **Cards that arrive mid-scroll arrive settled,** and the shared icon belongs to the card a detail
  page came from instead of every copy of it.

### Fixed

- **Archive downloads are saved in `Downloads/Aura Store`.** Zip, rar, iso and extensionless files
  are verified as archives, copied out of the cache and dropped from it. *Save file* warns when the
  name is already there and never overwrites; saved rows survive an emptied cache.
- **The build row no longer clips its last line** against the card's rounded corner, and a download
  taken from the source picker no longer repeats its whole label in the queue row.

## [1.7.0] - 2026-11-18

### Added

- **View all on every shelf and rail.** Each heading in Browse carries *View all*. The catalogue's
  rails open the full list in the rail's own order, fifty a page, walking the same query the row was
  made from. Every other server's rows lead to the site's own category, or to the source's whole
  catalogue when the row is a hand-picked selection with no term behind it. A list with no chip of
  its own is named by a heading above the grid.
- **Thirty posters per anime in the Special pack.** All thirty-one anime open into a gallery of
  thirty posters, 930 in all.
- **Thirty-one animated frames.** One for each anime in the pack, sixty-nine frames in all.
- **Posters can be deleted and brought back.** A hold takes a poster off the shelf and leaves a faded
  tile with a download mark; tapping the mark pulls the original art back from the project's raw
  files on GitHub. Both catalogues behave the same way.

### Changed

- **The You page holds one heartbeat.** Idle redraws fell from 219 frames in five seconds to 47.

### Fixed

- **A shelf that led to a list the site does not publish a chip for** now says which list it is
  instead of showing an unlabelled grid.

### Security

- **The credential sweep reads the working tree as well as the index,** so a secret that is staged
  and then changed is still named. Captured pages and screenshots are kept out of the repository.

## [1.6.0] - 2026-11-17

### Added

- **The You tab is a profile hub.** A cover, a picture and a frame at the top, then five tabs:
  Overview, Screen time, Wishlist, Updates and Style. The picture comes from the drawn set or from
  the gallery, cropped square; the cover is one of four drawn presets or a photo cropped wide.
- **Eight frames, worn everywhere.** None, Ring, Gold, Neon, Orbit, Laurel, Pulse and Ember. A frame
  turns around the picture on the profile page and in the top bar of every screen, and holds still
  when motion is switched off.
- **Screen time for apps installed from this store.** Today and all time per package, sorted by the
  time spent, behind Android's usage-access switch. Only packages this store installed are counted.
- **A wishlist.** The heart on an app page keeps the app in the You tab.
- **A splash and a first-run tour.** The mark is drawn while the catalogue loads, then three pages
  name the bar, the dock and the update path. About can show the tour again.
- **AMOLED black, as its own switch.** Appearance keeps the theme, palette, background and dock
  choices; AMOLED is a separate switch, not a background style.

### Changed

- **The A was redrawn.** One long letter, legs opened and the bar just above the middle, used by the
  splash, the avatars, the covers and the launcher. The old wide mark is gone.
- **Style is the fifth You tab,** so the label fits one line on a phone; the hero keeps the
  *Customise* word.
- **The top bar carries the chosen picture and frame** on every screen and updates as Style does.

### Fixed

- **The Updates empty state said the same thing twice.** It has its own body string now.
- **The avatar mark no longer reached into its frame,** and the cover mark lost the stray tail.

### Signing

The key that signed 1.5.0 was lost, and this release carries a replacement. Android refuses an
update over an install signed with another key, so a 1.5.0 copy from the releases page has to be
uninstalled once before 1.6.0 can be installed. The application id is unchanged, and 1.6.0 onward
keeps the new key.

The version is 1.6.0 and the version code is 10600.

## [1.5.0] - 2026-11-15

### Added

- **A source tab no longer ends at its front page.** Under the rows a site chose to feature is
  everything else it lists, newest first, filling in a page at a time as you scroll. The walk uses
  each site's own listing: 9MOD's posts route, 5play's games and apps listings, GetModsAPK's sitemap,
  AN1's paged home, ApkVision's update log.
- **An update banner for Aura Store.** When a check finds a newer GitHub release, a banner under the
  brand bar names the version and its size. It stays after the dialog's *Later*, and a tap reopens
  the offer without asking GitHub again.
- **The store's own download progress is pinned.** The bar, its percentage and its byte count sit
  above the release notes instead of scrolling with them, so the progress stays on screen while a
  long release body moves underneath.

### Fixed

- **The update progress was there and out of sight.** The bar lived at the bottom of the scrollable
  notes, which is below the fold on a normal release body. It now has its own block above them.

The version is 1.5.0 and the version code is 10500. The application id and the signing key are
unchanged, so this release updates an installed copy in place and keeps its data.

## [1.4.0] - 2026-11-15

### Changed

- **The update notification names the app.** One card per app with an update, carrying that app's
  own icon, its name, and its version pair as `v1.2.3 → v1.4.0`. The old card said how many apps had
  a newer build, which left you opening the store to find out which. The cards are ungrouped on
  purpose: a group collapses into a single summary and shows one icon for all of them.
- **One version pair, two places.** `Updates.delta` and `Updates.label` format the pair, so the You
  tab row and the notification print the same text instead of two near-copies.

### Fixed

- **A card leaves when its update does.** The packages posted by the last check are remembered, and
  the next check cancels any card whose app it did not report again.

The version is 1.4.0 and the version code is 10400. The application id and the signing key are
unchanged, so this release updates an installed copy in place and keeps its data.

## [1.3.0] - 2026-11-15

### Added

- **Every server tab opens on that site's own front page.** Browse keeps a tab per catalogue, and
  each tab now draws the rows the site draws, in the site's order: 5play's *Recommended*,
  *New games*, *Popular games*, *New apps* and *Popular apps*; GetModsAPK's *Featured*,
  *Updated Games* and *New Games*; AN1's *Latest games*; ApkVision's five game rows and three app
  rows; 9MOD's newest posts. The source's own name is a chip that returns to that page from any
  category.
- **The numbers each site prints.** A card carries the version and size its site lists, or the mod
  note, developer or rating where that is what the site publishes. A site whose cards carry no
  version is not given one.
- **Category counts** where the source publishes them, as the 9MOD terms do.

### Changed

- **A category is a card grid, not a rail.** Tapping a term opens the two-column listing the sites
  use, with the same version and size lines under each name.
- **The source pages keep the site's own split.** *New* and *Popular* stay separate rows instead of
  being flattened into one list, which is what made every server tab look like the same feed.

### Fixed

- **Scrolling no longer drags.** The navigation bar copied the whole page into a texture on every
  frame even at Blur 0, and paid for a render effect over the bar, so every list in the app stuttered
  while it moved. Only Blur asks for a copy now; at zero the bar is the drawn material, with its
  tint, sheen, lens band and colour rims, and the page is not sampled.
- **The enable switch was read as the shape.** `MainActivity` asked `glassReadsBackdrop` for
  `glass.floating` where it meant `glass.liquid`, so a flush bar could demand a page copy while a
  floating one did not.

The version is 1.3.0 and the version code is 10300. The application id and the signing key are
unchanged, so this release updates an installed copy in place and keeps its data.

## [1.2.0] - 2026-11-15

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
  `Aura-Store-v1.2.0-debug.apk` beside the signed APK, each with its `.sha256`.
  `tools/publish_release.py` uploads all four, and `release-notes/v1.2.0.md` says which build to
  install.
- **The store updates itself.** Settings carries "Update Aura Store automatically", the installed
  version and its code on a row of its own, and "Check now" beside the check's state. A newer
  GitHub release is offered with its notes, downloaded from the release, checked against the
  SHA-256 GitHub publishes for the asset, and handed to the system installer. The asset follows
  the signature of the running build: a debug install takes the debug APK, a release install the
  signed one.
- **One number, two readings.** `versionName` is `MAJOR.MINOR.PATCH`, and `versionCode` is derived
  from it in `app/build.gradle.kts` as `major × 10 000 + minor × 100 + patch`, so `1.2.0` is
  `10200`. `AppVersion` reads the same shape from a GitHub tag, orders `1.10.0` above `1.9.9`, and
  puts a pre-release below the release it leads to.

### Changed

- **Browse is one tab per server.** The catalogue tab keeps the LiteAPKs feed, and every other
  catalogue gets a tab with its own categories and its apps read a page at a time, so a server's
  tab reaches what its website lists rather than the newest handful. The stacked shelf view is gone.
- **New and updated comes first.** Browse opens on *New & updated*, with *Top charts* and
  *Trending* behind it, instead of leading with the view count.
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
- **The documents quote the build they ship.** 358 tests, versionCode 10200, and both APKs
  published beside their checksums.

### Fixed

- **The You tab stops offering an update that has just gone in.** The scan is keyed on the install
  list, so an install redraws the tab, and a row whose installed build already matches the
  catalogue's leaves it. The sweep also forgets its cached verdict for the post, because that
  verdict named the build the install replaced.
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
