# Contributing to Aura Store

Aura Store is an Android client in the shape of the Google Play Store, built around the
[liteapks.com](https://liteapks.com) catalogue and five other catalogues it can read. It is MIT
licensed, and the full text is in [`LICENSE`](LICENSE). Take the code, change it, ship it.

This file says how a change gets made, checked and sent.

## What a change has to pass

Three gates run on every push, and CI runs the same three.

```bash
python tools/sweep_secrets.py     # no credential in the tree the repository publishes
python tools/prose_gate.py        # the writing holds its own standard
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The first two are unusual, so here is why they are there.

**The sweep** reads every tracked file and looks for the shapes a credential takes: a cloud key, a
token, a private key, a password written into a file, a pre-signed signature, a widget nonce. It
fails the build when it finds one. The pages the parsers are tested against are copies of real
pages, and a real page can carry a key belonging to the site it came from. Those were redacted once;
the sweep is what keeps the next one out. If a fixture you add trips it, replace the value with a
placeholder that carries a space rather than weakening the pattern.

**The prose gate** scores the documents the way a reader meets them. It looks for the tells of
writing that was generated rather than written: padding, false authority, em dashes where a
sentence would do. `README.md`, `CHANGELOG.md` and the files under `docs/` are held to 90 or better,
and the gate has a self test. If the gate objects to a sentence, the sentence is usually the thing
that is wrong.

The third gate is the ordinary one: the code compiles, the tests pass, and lint is clean. The suite
is 338 JVM tests in 44 classes, and there are instrumented tests for the parts a JVM test cannot
reach. Those need a device attached:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Building it

JDK 17 and an Android SDK with API 37. Then:

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK, unsigned unless a key is configured
```

The release build reads its key from `local.properties`, which stays out of the repository. Without
those four values the build still succeeds and writes `app-release-unsigned.apk`, because the file
name says what is wrong with it.

```properties
AuraStoreFile=/absolute/path/to/your.jks
AuraStorePassword=...
AuraStoreKeyAlias=...
AuraStoreKeyPassword=...
```

**Use your own key.** The key that signs the published build belongs to the project and is not in
the repository. If you ship a build of your own, sign it with your own key.

**Change the application id if you want your build to sit beside the official one.** Android ties an
update to the signing key: an app signed with a different key is a different app as far as the
installer is concerned, and it cannot replace an installed copy. Keeping `dev.aurastore.store` and
signing it with another key means the two builds cannot update each other, and whichever one a
phone installs first, the other will refuse to replace it.

## Sending a change

1. Fork the repository and work on a branch.
2. Keep the change to one thing. A pull request that fixes a bug and renames a class is two.
3. Write the reasoning down. The code says what it does; the comment says why it is that way, and
   the comment is for the person who comes next.
4. Add the test with the change. The suite is the memory of this project, and a fix without a test
   is a fix that can be lost.
5. Add a line to [`CHANGELOG.md`](CHANGELOG.md) under *Unreleased*, in the voice the file already
   uses.
6. Open the pull request. Say what changed, what it fixes, and how you checked it.

The history of this repository is one commit, because the project was assembled in one sitting and
squashed afterwards. Commits from now on are ordinary commits.

## The shape of the code

- Kotlin and Jetpack Compose, no dependency injection, no database: singletons, `StateFlow` and
  `SharedPreferences`.
- Public surfaces carry KDoc that says what the thing is for and what it refuses to do. A function
  that can fail says how it fails.
- A comment that repeats the code is noise. A comment that records a measurement, a rule that was
  measured on a phone, or a decision that looked wrong and turned out to be right is the point.
- Tests live beside the code in `src/test`, and they are named after the rule they pin, not after
  the method they call: `the query string of a signed link never reaches the file name`.
- The app talks to the catalogues over TLS and refuses anything else. `SecureTransport` is the one
  place that rule lives, and every HTTP client in the app goes through it.

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md)
are the orientation documents: what runs where, and which invariants the code is not allowed to
break.

## Reporting a problem

A bug goes in the issue tracker. Something that should not be public, a leak of a key, or a
vulnerability goes through [`SECURITY.md`](SECURITY.md) instead, which says how to report it in
private.

## Credits

The people who run the catalogues this app reads are named in the README. If you add a source, add it
there too, and say what it is and where its files live.
