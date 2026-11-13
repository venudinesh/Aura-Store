#!/usr/bin/env python3
"""Publish a release of Aura Store on GitHub.

The release carries the signed APK and the debug APK, each with its sha-256.
The token is read from the environment and is never written anywhere:

    AURA_STORE_TOKEN=github_pat_... python tools/publish_release.py v1.1.0

The token needs "Contents: read and write" on the repository. A token with
metadata only can read the repository and nothing else, which is the failure
this script reports when it gets a 403 back.

The release notes are the file under `release-notes/` that carries the tag's
name. A version without one gets the notes GitHub writes from the commits.

Running it again on a release that already exists is the supported path for a
re-cut: the notes are refreshed from the file, and an asset whose bytes differ
from the local build is replaced. The same name is kept, so the link a reader
already has keeps working.

The debug APK ships beside the release APK, under `-debug`. It is debug-signed,
so it cannot replace or update a release install; it is there for testing.

Stdlib only, so it runs with the python that is already on the machine.
"""

from __future__ import annotations

import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "https://api.github.com"
REPOSITORY = os.environ.get("AURA_STORE_REPOSITORY", "venudinesh/Aura-Store")
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
DEBUG_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"


class ApiError(Exception):
    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status


def call(method: str, path: str, token: str, body=None, raw: bytes | None = None,
         content_type: str | None = None, url: str | None = None):
    target = url or (API + path)
    request = urllib.request.Request(target, method=method, data=raw)
    request.add_header("Authorization", "Bearer " + token)
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    if content_type:
        request.add_header("Content-Type", content_type)
    elif body is not None:
        request.add_header("Content-Type", "application/json")
    if body is not None:
        request.data = json.dumps(body).encode("utf-8")
    try:
        with urllib.request.urlopen(request) as response:
            return response.read(), response.headers
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise ApiError(error.code, "%s %s failed: HTTP %s\n%s" % (method, target, error.code, detail)) from error


def existing_release(tag: str, token: str):
    """The release for a tag, or None when there is none. A 403 is not 'none'."""
    try:
        payload, _ = call("GET", "/repos/%s/releases/tags/%s" % (REPOSITORY, tag), token)
    except ApiError as error:
        if error.status == 404:
            return None
        raise
    return json.loads(payload)


def refresh(release: dict, notes: Path, token: str):
    """Update the name and the notes of an existing release from the local file."""
    body = {
        "name": "Aura Store %s" % release["tag_name"].lstrip("v"),
    }
    if notes.is_file():
        body["body"] = notes.read_text(encoding="utf-8")
    payload, _ = call("PATCH", "/repos/%s/releases/%d" % (REPOSITORY, release["id"]), token, body=body)
    return json.loads(payload)


def upload(release_id: int, name: str, data: bytes, token: str, content_type: str):
    url = "https://uploads.github.com/repos/%s/releases/%d/assets?name=%s" % (
        REPOSITORY, release_id, name)
    request = urllib.request.Request(url, method="POST", data=data)
    request.add_header("Authorization", "Bearer " + token)
    request.add_header("Accept", "application/vnd.github+json")
    request.add_header("X-GitHub-Api-Version", "2022-11-28")
    request.add_header("Content-Type", content_type)
    request.add_header("Content-Length", str(len(data)))
    with urllib.request.urlopen(request) as response:
        return json.loads(response.read())


def delete_asset(asset_id: int, token: str):
    call("DELETE", "/repos/%s/releases/assets/%d" % (REPOSITORY, asset_id), token)


def fetch_asset(asset: dict, token: str) -> bytes:
    """The bytes of an existing asset, so a same-size file is not mistaken for the same file."""
    request = urllib.request.Request(asset["url"], method="GET")
    request.add_header("Authorization", "Bearer " + token)
    request.add_header("Accept", "application/octet-stream")
    try:
        with urllib.request.urlopen(request) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise ApiError(error.code, "GET %s failed: HTTP %s\n%s" % (asset["url"], error.code, detail)) from error


def place(release: dict, name: str, data: bytes, token: str, content_type: str):
    """Put one asset on the release, replacing a copy whose bytes differ."""
    for asset in release.get("assets", []):
        if asset["name"] != name:
            continue
        if hashlib.sha256(fetch_asset(asset, token)).hexdigest() == hashlib.sha256(data).hexdigest():
            print("asset:   %s (unchanged, %d bytes)" % (name, asset["size"]))
            return
        print("asset:   %s (replacing %d bytes with %d)" % (name, asset["size"], len(data)))
        delete_asset(asset["id"], token)
    asset = upload(release["id"], name, data, token, content_type)
    if asset["size"] != len(data):
        raise SystemExit("upload of %s reported %d bytes, expected %d" % (name, asset["size"], len(data)))
    print("asset:   %s (%d bytes)" % (asset["name"], asset["size"]))


def checksum_text(tag: str, name: str, digest: str) -> bytes:
    return ("%s  %s\n" % (digest, name)).encode("ascii")


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: AURA_STORE_TOKEN=... python tools/publish_release.py <tag> [target]")
        return 2
    tag = sys.argv[1]
    target = sys.argv[2] if len(sys.argv) > 2 else "kotlin"

    token = os.environ.get("AURA_STORE_TOKEN", "").strip()
    if not token:
        print("AURA_STORE_TOKEN is not set.")
        return 2
    for path, command in ((APK, ":app:assembleRelease"), (DEBUG_APK, ":app:assembleDebug")):
        if not path.is_file():
            print("Missing %s. Run: ./gradlew %s" % (path.relative_to(ROOT), command))
            return 2

    release_apk = APK.read_bytes()
    debug_apk = DEBUG_APK.read_bytes()
    release_digest = hashlib.sha256(release_apk).hexdigest()
    debug_digest = hashlib.sha256(debug_apk).hexdigest()
    release_name = "Aura-Store-%s.apk" % tag
    debug_name = "Aura-Store-%s-debug.apk" % tag
    print("apk:     %s (%d bytes)" % (APK.relative_to(ROOT), len(release_apk)))
    print("sha256:  %s" % release_digest)
    print("debug:   %s (%d bytes)" % (DEBUG_APK.relative_to(ROOT), len(debug_apk)))
    print("sha256:  %s" % debug_digest)

    notes = ROOT / "release-notes" / ("%s.md" % tag)
    try:
        release = existing_release(tag, token)
        if release is None:
            body = {
                "tag_name": tag,
                "name": "Aura Store %s" % tag.lstrip("v"),
                "target_commitish": target,
                "draft": False,
                "prerelease": False,
            }
            if notes.is_file():
                body["body"] = notes.read_text(encoding="utf-8")
            else:
                body["generate_release_notes"] = True
            payload, _ = call("POST", "/repos/%s/releases" % REPOSITORY, token, body=body)
            release = json.loads(payload)
            print("release: %s (created)" % release["html_url"])
        else:
            release = refresh(release, notes, token)
            print("release: %s (refreshed)" % release["html_url"])

        place(release, release_name, release_apk, token,
              "application/vnd.android.package-archive")
        place(release, release_name + ".sha256",
              checksum_text(tag, release_name, release_digest), token, "text/plain")
        place(release, debug_name, debug_apk, token,
              "application/vnd.android.package-archive")
        place(release, debug_name + ".sha256",
              checksum_text(tag, debug_name, debug_digest), token, "text/plain")
    except ApiError as error:
        print(error)
        return 1

    print("done:    %s" % release["html_url"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
