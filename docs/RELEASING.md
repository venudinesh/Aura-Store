# Releasing Aura Store

A release is the signed APK, the debug APK, and a checksum file for each, published on a tag. The
signing happens on the machine that holds the key, and the publishing is done by hand or by a
script that reads a token from the environment. Nothing about the key leaves the machine.

## One-time setup on the release machine

The release key lives in `local.properties`, which the `.gitignore` keeps out of the repository:

```properties
AuraStoreFile=/absolute/path/to/aurastore-release.jks
AuraStorePassword=...
AuraStoreKeyAlias=aurastore
AuraStoreKeyPassword=...
```

The same four names work as environment variables, which is what the build reads when the
properties file is not there.

The keystore, its passwords, `local.properties` and any copy of the key are all covered by
`.gitignore`. None of them belongs in the repository, and none of them belongs in a chat.

## Cutting a release

1. Bump `versionCode` and `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts). The tag
   is `v` plus `versionName`, so `versionName = "1.1.0"` and tag `v1.1.0`.
2. Write the notes for the version in [`release-notes/v1.1.0.md`](../release-notes/) and add the
   dated section at the top of [`CHANGELOG.md`](../CHANGELOG.md).
3. Run the gates:

   ```bash
   python tools/sweep_secrets.py
   python tools/prose_gate.py
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

4. Build both APKs and check the signature before anything is published:

   ```bash
   ./gradlew :app:assembleRelease :app:assembleDebug
   apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
   sha256sum app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/debug/app-debug.apk
   ```

   The release build is not byte-reproducible: two builds of the same sources differ in the zip
   metadata. Read the hash from the file that ships, not from an earlier build of the same commit.

5. Commit, tag and push:

   ```bash
   git tag v1.1.0
   git push origin kotlin
   git push origin v1.1.0
   ```

6. Publish the four files: both APKs and the checksum beside each.

   ```bash
   AURA_STORE_TOKEN=github_pat_... python tools/publish_release.py v1.1.0
   ```

   The token needs *Contents: read and write* on the repository. The script reads the tag's notes
   from `release-notes/` and uploads:

   - `Aura-Store-v1.1.0.apk`, the signed release build, and `Aura-Store-v1.1.0.apk.sha256`.
   - `Aura-Store-v1.1.0-debug.apk`, the debug build, and `Aura-Store-v1.1.0-debug.apk.sha256`.

   The token stays out of every file it writes.

   Running it again on a release that is already published refreshes the notes from the same file
   and replaces an asset whose bytes differ from the local build, under the same name. A re-cut does
   not need the old files deleted by hand.

   Without a token, the same four files can be attached to the release page by hand.

7. Download the published files from outside and check them again. The size, the digest and the
   certificate are the three things worth reading twice, and the checksum file beside each APK is
   what a reader will compare against. The release APK's certificate is the project key; the debug
   APK's is the debug key, and that is the reason Android will not let one replace the other.

## Why the release is not done by a workflow

The signing pipeline used to live in a GitHub Actions workflow that read the keystore from the
repository's secret store. It is not there any more, and the reason is the key itself.

A workflow that signs has to be trusted with the release key: the keystore, the store password
and the key password all have to sit in the repository's secret store for it to work. That is a
sensible arrangement, and it is also the one arrangement where a mistake in the workflow, a
compromised runner or a careless secret turns into a key that anyone who can push a tag can use.
Signing on the machine that holds the key keeps one copy of it, in one place, and the publish step
becomes an upload of four files.

Uploading is a separate job, and a workflow can do it without the key. A `contents: write` job
that runs `gh release upload` on a throwaway branch publishes the files the machine already signed.
That is the fallback when the stored token cannot write: the repository's own Actions token can,
and no secret is involved.

The cost is that a release is a person doing six steps rather than a tag doing them. The
[CI workflow](../.github/workflows/ci.yml) still runs on every push, so the build, the tests, the
lint and the two gates are still checked on every change.

## What the release is, and is not

- The release key is separate from the debug key, so a release build cannot install over a debug
  build. Uninstalling the debug build removes its queue, settings and remembered installs.
- R8 and resource shrinking are on. The shrunk artefact is installed and smoke-tested on the phone,
  which is what justifies the keep rules in `app/proguard-rules.pro`.
- The APK targets API 36. Raising the target to 37 is a behaviour change that no device here can
  exercise, so it stays at 36 until that pass exists.
- A fork publishes with its own key, and with its own application id, because Android ties an
  update to the signing key. [`CONTRIBUTING.md`](../CONTRIBUTING.md) says the same thing from the
  contributor's side.
