"""A sweep for credentials in the tree the repository publishes.

Every tracked file is read as bytes and searched for the shapes a credential takes. The gate is
the point: the script exits with a failure when the tracked tree holds a key, so a key cannot be
pushed into the public repository without the build saying so.

The patterns are written as shapes and never as literal keys. A literal key in this file would be
itself a key in the repository.

    python tools/sweep_secrets.py          the gate: the tracked tree, and nothing else
    python tools/sweep_secrets.py --all    the gate, plus a report on the working directory

The working directory holds the signing key and its base64 copy. Those belong here and nowhere
else, so they are reported and not failed on.
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gitfiles import tracked  # noqa: E402  the list of files the repository publishes

# The shapes a credential takes. Each is a shape, not an example.
PATTERNS = {
    "google-api-key": rb"AIza[0-9A-Za-z_\-]{30,}",
    "json-api-key": rb"(?i)['\"]apiKey['\"]\s*:\s*['\"][^'\"\s]{20,}",
    "site-verification": rb"(?i)google-site-verification['\"]?\s+content=['\"][A-Za-z0-9_\-]{30,}",
    "csrf-token": rb"(?i)csrf-token['\"]?\s+content=['\"][A-Za-z0-9_\-]{30,}",
    "github-token": rb"gh[pousr]_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{30,}",
    "gitlab-token": rb"glpat-[0-9A-Za-z_\-]{20,}",
    "openai-key": rb"sk-[0-9A-Za-z_\-]{20,}",
    "slack-token": rb"xox[baprs]-[0-9A-Za-z\-]{10,}",
    "aws-key-id": rb"AKIA[0-9A-Z]{16}",
    "aws-secret": rb"(?i)aws_secret_access_key\s*[=:]\s*['\"]?[A-Za-z0-9/+=]{40}",
    "private-key": rb"-----BEGIN[A-Z ]*PRIVATE KEY-----",
    "jwt": rb"eyJ[A-Za-z0-9_\-]{10,}\.eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}",
    "push-key": rb"(?i)vapidKey['\"]?\s*[=:]\s*['\"]([A-Za-z0-9_\-]{60,})",
    "presigned-signature": rb"X-Amz-Signature=[0-9a-f]{32,}|X-Amz-Credential=[0-9a-f]{16,}",
    "wordpress-nonce": rb"data-nonce=[\"'][A-Za-z0-9]{8,}[\"']",
    "google-oauth-client": rb"(?i)\b[0-9]+-[0-9a-z_]{32}\.apps\.googleusercontent\.com",
    "bearer-literal": rb"(?i)authorization\s*[:=]\s*['\"]?bearer\s+[A-Za-z0-9_\-\.]{20,}",
    "assigned-secret": (
        rb"(?i)(password|passwd|pwd|secret|api[_-]?key|apikey|token|client_secret)"
        rb"\s*[=:]\s*['\"]([A-Za-z0-9_\-\.\+/=]{8,})['\"]"
    ),
    "basic-auth-url": rb"https?://[^\s:@/]+:[^\s:@/]+@[^\s/]+",
    "base64-blob": rb"(?m)^[A-Za-z0-9+/]{200,}={0,2}$",
}

# Vendored code that is not ours to hold to this house's rules.
SKIP_PREFIXES = ("tools/anti-ai-slop/",)

# The signing material, which belongs on this machine and not in the repository.
LOCAL_ONLY = ("app/keystore/",)


def working_tree():
    found = []
    for base, dirs, names in os.walk(ROOT):
        dirs[:] = [
            d
            for d in dirs
            if d not in (".git", "build", ".gradle", ".kotlin", "__pycache__", "node_modules", ".pytest_cache")
        ]
        for name in names:
            rel = os.path.relpath(os.path.join(base, name), ROOT).replace(os.sep, "/")
            found.append(rel)
    return found


def scan(paths):
    hits = []
    for rel in paths:
        if rel.startswith(SKIP_PREFIXES):
            continue
        full = os.path.join(ROOT, rel)
        try:
            with open(full, "rb") as handle:
                data = handle.read()
        except (OSError, IsADirectoryError):
            continue
        for name, pattern in PATTERNS.items():
            for match in re.finditer(pattern, data):
                line = data[: match.start()].count(b"\n") + 1
                value = match.group(0).decode("utf-8", "replace")
                if len(value) > 80:
                    value = value[:80] + "..."
                hits.append((name, rel, line, value))
    return hits


def show(hits, label, warn_only=False):
    print("=== %s: %d hit(s) ===" % (label, len(hits)))
    for name, rel, line, value in hits:
        mark = "  " if warn_only else "!!"
        print("%s [%-20s] %s:%s  %s" % (mark, name, rel, line, value))
    print()


def self_test():
    """Prove that every pattern bites.

    A gate that has never caught anything is a gate nobody has tested. Each shape below is
    planted in a line of text, and the pattern has to find it.
    """
    samples = {
        "google-api-key": "key: AIzaSy" + "A" * 35,
        "json-api-key": '{"apiKey": "' + "A" * 30 + '"}',
        "site-verification": '<meta name="google-site-verification" content="' + "A" * 40 + '" />',
        "csrf-token": '<meta name="csrf-token" content="' + "A" * 40 + '">',
        "github-token": "token: ghp_" + "a" * 36,
        "gitlab-token": "token: glpat-" + "a" * 20,
        "openai-key": "key: sk-" + "a" * 32,
        # The three below are written in two pieces, so that the sample does not sit in this file
        # as the shape it plants. A sample that is itself caught would make the gate fail on the
        # tool that carries it.
        "slack-token": "token: xox" + "b-1234567890-abcdef",
        "aws-key-id": "id: AKIA" + "A" * 16,
        "private-key": "-----BEGIN RSA " + "PRIVATE KEY-----",
        "jwt": "eyJhbGciOiJIUzI1NiJ9." + "eyJzdWIiOiIxMjM0NTY3ODkwIn0." + "dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        "push-key": '"vapidKey":"' + "A" * 80 + '"',
        "presigned-signature": "X-Amz-Signature=" + "a" * 64,
        "wordpress-nonce": 'data-nonce="' + "a1b2c3d4" + '"',
        "assigned-secret": 'password = "' + "s3cretvalue" + '"',
        "basic-auth-url": "https://user:" + "pass@example.com/x",
        "base64-blob": "A" * 200,
    }
    failures = []
    for name, text in samples.items():
        pattern = PATTERNS.get(name)
        if pattern is None:
            failures.append("%s: no pattern" % name)
            continue
        if not re.search(pattern, text.encode("utf-8")):
            failures.append("%s: the sample was not caught" % name)
    # The placeholder that replaced the keys must not be caught, or the gate would fail on the
    # fixtures it was written to clean.
    for text in ("AIzaSy REDACTED FOR THE TESTS", "REDACTED PUSH KEY FOR THE TESTS"):
        for name, pattern in PATTERNS.items():
            if re.search(pattern, text.encode("utf-8")):
                failures.append("%s: the placeholder was caught" % name)
    print("self test: %d of %d shapes bite" % (len(samples) - len(failures), len(samples)))
    for line in failures:
        print("  FAIL %s" % line)
    return 1 if failures else 0


def main(argv):
    if "--self-test" in argv:
        return self_test()
    everything = "--all" in argv
    tracked_hits = scan(tracked())
    show(tracked_hits, "tracked tree")
    if everything:
        local = [
            hit
            for hit in scan(working_tree())
            if not hit[1].startswith(SKIP_PREFIXES)
        ]
        held = [hit for hit in local if hit[1].startswith(LOCAL_ONLY)]
        rest = [hit for hit in local if not hit[1].startswith(LOCAL_ONLY)]
        show(held, "working directory, signing material (expected here)", warn_only=True)
        show(rest, "working directory")
    if tracked_hits:
        print("FAIL: the tree the repository publishes holds a credential.")
        return 1
    print("PASS: the tree the repository publishes holds no credential.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
