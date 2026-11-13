# Aura Store: the user guide

Aura Store is a shop front for Android apps that are published outside Google Play. It reads a set of
catalogue sites, shows what they have, and downloads the file from the site you pick. It hosts
nothing of its own.

This guide is for someone using the app. The code and the design are described in
[PROJECT_GUIDE.md](PROJECT_GUIDE.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

## What you need

- Android 8.1 (API 27) or newer.
- About 20 MB for the app, plus room for whatever you download.
- A working internet connection. Every shelf, search and download is a request to a catalogue site.

## Install the app

1. Open the [releases page](https://github.com/venudinesh/Aura-Store/releases) and download
   `Aura-Store-v1.1.0.apk`.
2. If you want to check the file first, download `Aura-Store-v1.1.0.apk.sha256` beside it and compare:

   ```bash
   sha256sum Aura-Store-v1.1.0.apk
   ```

   The two strings have to match. The same digest is printed on the release page.
3. Open the APK. Android asks whether your browser or file manager may install unknown apps. Allow it
   for that app, then tap Install.
4. Open Aura Store. The Games tab loads the catalogue.

The app is not distributed through Google Play, so Android treats it as a sideloaded package. The
warning Android shows is the standard one for every app installed this way.

## Let Aura Store install apps

Android does not let one app install another without a switch. Aura Store asks for that switch once.

1. Open the avatar in the top bar.
2. Choose Settings, then Security, then *Install unknown apps*.
3. Turn the switch on. Android still shows its own confirmation for every package.

The same row is in the avatar sheet as *Install permission*. If a download finishes and no installer
opens, this switch is the first thing to check. The file stays in the queue until the install can
run.

## The five tabs

The bar along the bottom holds Games, Apps, Search, Browse and You.

**Games and Apps** are the catalogue's own shelves. Each tab shows the rows the catalogue publishes
for that section: new apps, popular apps, and the categories it groups them under. The ordering and
the labels come from the site, not from the app. A shelf that runs out says so in its footer, and a
shelf that failed offers a retry.

**Search** asks the catalogue and every enabled source at once. The sites that answer become a row
of tabs, each with the number of matches it returned. One tab is on screen at a time: the catalogue's
own rows, or another site's.

A row from another site downloads from that site. If the site has one file, the download starts. If
it has several, a sheet opens with the files from that site, and you pick the one you want. The app
does not ask which site again, because the tab already answered that.

**Browse** carries a block for each source you have switched on: the apps that source published last,
and its categories. Tapping a source's name opens that source's own page, where its newest apps and
its categories can be read on their own.

**You** lists the apps installed on the phone, what has a newer version, and how much of the
catalogue was checked. Apps installed by Aura Store can update themselves. Apps installed from
elsewhere are matched back by their package name and wait for a tap. *Update all* runs every pending
update in one go.

## Downloading an app

Open an app's page and tap Install. The first time, a sheet asks where the file should come from.

- Every enabled source is listed, and each one says whether it has the app.
- The catalogue's own build is in the list with the other sites.
- The default source, if you set one in Settings, is already expanded and marked *Default*.
- Each answer is resolved to a direct file link before the download starts, so the sheet shows the
  file name, the kind (apk or xapk) and the size where the site publishes it.

Pick a source and the download joins the queue. The floating panel above the navigation bar shows
progress, and the notification does the same when the app is in the background.

A download can be paused and resumed. A paused row keeps its bytes, and resuming asks the server for
the rest. The digest still covers the whole file at the end.

## What the app checks before it installs

1. The SHA-256 is computed while the bytes arrive, so the file is read once.
2. Android's package parser reads the file: package name, version code and the key that signed it.
3. If the source publishes a digest, the two are compared. A mismatch deletes the file and fails the
   row.
4. The result is on screen before the install button matters: host, size, digest, package name,
   version and signer.

That proves the file arrived intact, is a valid package, and carries the same signing key as the copy
you installed last time. It does not prove the app is safe, and it does not prove the app came from
the original developer. A signature says who signed the file, nothing more.

## Installing and updating

Android's installer opens for every package and asks for confirmation. That prompt belongs to
Android, and Aura Store cannot skip it. Once you confirm, the install continues and the row reports
the result.

The You tab keeps the list current. The app checks the catalogue when the tab opens and when you ask
it to. Settings has *Auto-update apps*, which installs newer versions of apps that came from Aura
Store without waiting for a tap. *Check for updates* runs the check immediately and says when it last
ran.

An app that was installed from somewhere else is listed as *Installed outside Aura Store*. Its update
is matched by package name, and the app waits for your tap. If an update is not wanted, *Ignored
update* hides it; *Show again* brings it back.

## Games with OBB data

Some large games ship their data in an OBB folder beside the APK. Aura Store can place that folder in
two ways.

- On a phone that grants root, the app copies the folder to `Android/obb/<package>` after the
  install. The prompt says what it is for, and saying no leaves the rest of the app working.
- Without root, the app exports the folder to `Downloads/AuraStore/<package>` and shows the path the
  game expects. Move the folder there with a file manager.

The app asks for root for that one job. It does not use it for anything else.

## Settings

The avatar in the top bar opens the account sheet: Manage apps, Downloads, Install permission,
Settings and About.

Settings holds:

- **Updates.** Auto-update, check for updates, the last sync, and this build's version.
- **Security.** The install permission switch, download notifications, and a short explanation of why
  Android requires the switch.
- **Catalogue sources.** LiteAPKs is the app's own feed and cannot be switched off. The other five
  can be. A source that is off stops appearing in the search and in the source sheet.
- **Default download source.** Where a download comes from when you have not chosen another source.
- **Storage.** How many downloads are in the queue and how much they take, *Clear finished*, and
  *Save downloaded APKs*.
- **Appearance.** Light, dark or system theme, and the palette.
- **About.** What the app is, and the disclaimer that it is not affiliated with Google Play.

## Storage

Downloads are written to the app's cache while they are in the queue. *Clear finished* removes the
finished rows and the files they own. A paused download keeps its bytes.

*Save downloaded APKs* copies each finished file to `Downloads/AuraStore`, where it survives clearing
the queue and uninstalling the app. OBB exports go to `Downloads/AuraStore/<package>` as well.

Android backup is switched off, so the queue, the settings and the remembered installs stay on the
phone. Uninstalling the app removes them.

## Privacy and network

- No account and no sign-in. The avatar is a local picture, not a login.
- No analytics, no crash reporting and no advertising identifier.
- Nothing is uploaded. Every request is a request to read a site.
- Transport is TLS only. The manifest forbids cleartext traffic, and every HTTP client refuses a
  request whose address is not `https`, including a redirect in the middle of a download.

The sites the app reads are listed in the [README](../README.md). Each one keeps the ownership of its
content and its trademarks.

## When something goes wrong

**A search finds nothing on a site.** That site may be down, may be blocking automated requests, or
may not have the app. Open Browse and that source's own page to see whether it answers at all.
Try another source, and check that its switch is on in Settings.

**A download does not start.** Check the connection and the free space. Pause and resume the row. If
the row says the source refused the link, pick another source: the signed link on one site can expire
while another site still has the file.

**The download finishes but no installer opens.** Allow installs for Aura Store, as described above.
The queue keeps the file, so the install continues once the switch is on.

**Android says "App not installed".** The phone already has a copy signed with a different key. An app
signed by someone else cannot replace it. Uninstall the old copy first, which removes its data, or
keep the copy you have.

**The digest does not match.** The file was altered or truncated in transit. Aura Store deletes it
and fails the row. Try again, or use another source.

**An app shows an older version than the site.** Open You and run *Check for updates*. If the app was
installed outside Aura Store, the update waits for your tap rather than installing itself.

**A game cannot find its data.** Check that the OBB folder is at `Android/obb/<package>`. Without
root, the app exports it to `Downloads/AuraStore/<package>` and shows the path; move it from there.

## Uninstall

Uninstall Aura Store like any other app, from Android's settings or by long-pressing its icon. The
queue and the settings go with it. Saved APKs in `Downloads/AuraStore` stay, because they belong to
the phone's storage rather than to the app.

## Questions

**Is a downloaded app safe?** Aura Store checks the file, not the app's behaviour. It proves the
download arrived intact and is a valid package with a consistent signing key. It cannot prove the app
does nothing harmful, and it cannot prove it came from the original developer. Treat a modded
catalogue the way you would treat any file from the internet.

**Why does Android warn about the app?** Every app installed outside Google Play gets the same
warning. The switch in Settings is Android's rule for the installer hand-off, not a permission Aura
Store invented.

**Which source should I pick?** The one that has the file you want. The catalogue's own build is the
default for a reason: it is the feed this app is built around. Other sites sometimes carry a newer
version or a different mod.

**Does the app need root?** No. Root is used for one optional job, placing an OBB folder. Every other
feature works without it.

**Does Aura Store update apps it did not install?** It can match them by package name and offer the
update, but the install still needs your tap, and Android still shows its confirmation.
