"""A wide sweep for anything that must not sit in a public repository.

This is broader than the gate in `sweep_secrets.py`. The gate decides whether the build may go
ahead; this one is the reading of the whole tree, and it also reports the softer findings: a
personal path, a private address, a cleartext link, a name.

    python tools/audit_leaks.py            the tracked tree
    python tools/audit_leaks.py --all      the working directory as well
"""

import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gitfiles import tracked  # noqa: E402  the list of files the repository publishes

# Patterns that say something is wrong. Each is (name, regex).
SECRETS = [
    ("cloud-api-key", rb"AIza[0-9A-Za-z_\-]{30,}"),
    ("firebase-key", rb"(?i)['\"]apiKey['\"]\s*:\s*['\"][^'\"\s]{20,}"),
    ("firebase-config", rb"(?i)firebase(messaging)?\.googleapis\.com"),
    ("github-token", rb"gh[pousr]_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{30,}"),
    ("gitlab-token", rb"glpat-[0-9A-Za-z_\-]{20,}"),
    ("openai-key", rb"sk-[0-9A-Za-z_\-]{20,}"),
    ("slack-token", rb"xox[baprs]-[0-9A-Za-z\-]{10,}"),
    ("discord-webhook", rb"discord(?:app)?\.com/api/webhooks/\d+/[\w\-]+"),
    ("telegram-token", rb"\b\d{8,10}:[A-Za-z0-9_\-]{35}\b"),
    ("aws-key-id", rb"AKIA[0-9A-Z]{16}"),
    ("aws-secret", rb"(?i)aws_secret_access_key\s*[=:]\s*['\"]?[A-Za-z0-9/+=]{40}"),
    ("gcp-service-account", rb"(?i)private_key_id\s*[=:]\s*['\"][A-Za-z0-9_\-]{20,}"),
    ("azure-connection", rb"(?i)AccountKey=[A-Za-z0-9+/=]{40,}"),
    ("stripe-key", rb"\b[sr]k_(live|test)_[0-9A-Za-z]{20,}"),
    ("twilio", rb"\bSK[0-9a-fA-F]{32}\b"),
    ("sendgrid", rb"SG\.[A-Za-z0-9_\-]{20,}\.[A-Za-z0-9_\-]{20,}"),
    ("mailgun", rb"key-[0-9a-f]{32}"),
    ("npm-token", rb"\bnpm_[A-Za-z0-9]{36}\b"),
    ("pypi-token", rb"pypi-AgEIcHlwaS5vcmc[A-Za-z0-9_\-]{50,}"),
    ("private-key-block", rb"-----BEGIN[A-Z ]*PRIVATE KEY-----"),
    ("putty-private-key", rb"PuTTY-User-Key-File-\d"),
    ("jwt", rb"eyJ[A-Za-z0-9_\-]{10,}\.eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}"),
    ("push-key", rb"(?i)vapidKey['\"]?\s*[=:]\s*['\"]([A-Za-z0-9_\-]{60,})"),
    ("presigned-signature", rb"X-Amz-Signature=[0-9a-f]{32,}|X-Amz-Credential=[0-9a-f]{16,}"),
    ("wordpress-nonce", rb"data-nonce=[\"'][A-Za-z0-9]{8,}[\"']"),
    ("cloudflare-bucket-id", rb"[0-9a-f]{32}\.r2\.cloudflarestorage\.com"),
    ("site-verification", rb"(?i)google-site-verification['\"]?\s+content=['\"][A-Za-z0-9_\-]{30,}"),
    ("csrf-token", rb"(?i)csrf-token['\"]?\s+content=['\"][A-Za-z0-9_\-]{30,}"),
    ("recaptcha", rb"(?i)6L[0-9A-Za-z_\-]{38}"),
    ("assigned-secret", rb"(?i)(password|passwd|pwd|secret|api[_-]?key|apikey|token|client_secret|passphrase)\s*[=:]\s*['\"]([A-Za-z0-9_\-\.\+/=]{8,})['\"]"),
    ("connection-string", rb"(?i)(mongodb(\+srv)?|postgres(ql)?|mysql|redis|amqp)://[^\s'\"]+:[^\s'\"@]+@"),
    ("basic-auth-url", rb"https?://[^\s:@/]+:[^\s:@/]+@[^\s/]+"),
    ("base64-blob", rb"(?m)^[A-Za-z0-9+/]{200,}={0,2}$"),
    ("keystore-magic", rb"MII[A-Za-z0-9+/]{20,}"),
]

# Patterns that are not secrets but should not be in a public tree.
PRIVACY = [
    ("windows-home-path", rb"[A-Z]:\\\\Users\\\\[A-Za-z0-9_\-]+"),
    ("unix-home-path", rb"/home/[a-z][a-z0-9_\-]{2,}"),
    ("email-address", rb"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"),
    ("private-ipv4", rb"\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"),
    ("phone-number", rb"(?<!\d)\+?\d{1,3}[ .-]?\(?\d{3}\)?[ .-]?\d{3}[ .-]?\d{4}(?!\d)"),
    ("device-serial", rb"(?i)(imei|imsi|android_id|device_id)\s*[=:]\s*['\"]?[A-Za-z0-9_\-]{8,}"),
    ("cleartext-url", rb"http://(?!localhost|127\.0\.0\.1|schemas\.android\.com|www\.w3\.org|xmlns|ns\.adobe|purl\.org|iptc\.org)[A-Za-z0-9.\-]+"),
    ("localhost-url", rb"https?://(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?"),
    ("tracking-id", rb"\b(UA-[0-9]{8,10}-[0-9]|G-[A-Z0-9]{8,12}|AW-[0-9]{8,12}|GTM-[A-Z0-9]{6,10})\b"),
    ("sentry-dsn", rb"https://[0-9a-f]{32}@[A-Za-z0-9.\-]*sentry[A-Za-z0-9.\-]*/[0-9]+"),
    ("analytics-host", rb"(?i)(google-analytics\.com|googletagmanager\.com|app-measurement\.com|mixpanel|amplitude|segment\.io|bugsnag|sentry\.io)"),
    ("telemetry-host", rb"(?i)(telemetry|analytics|tracking|beacon)[A-Za-z0-9.\-]*\.(com|io|net|org)"),
    ("internal-hostname", rb"\b[a-z0-9\-]+\.(internal|intranet|corp|local|lan)\b"),
    ("oidc-issuer", rb"(?i)(accounts\.google\.com|login\.microsoftonline\.com|keycloak|okta\.com|auth0\.com)"),
]

SKIP_PREFIXES = ("tools/anti-ai-slop/",)
LOCAL_ONLY = ("app/keystore/",)

# The places a local path is expected and harmless.
PATH_ALLOW = (
    "app/src/test/java/",
    "tools/",
)


def working_tree():
    found = []
    for base, dirs, names in os.walk(ROOT):
        dirs[:] = [
            d
            for d in dirs
            if d
            not in (
                ".git",
                "build",
                ".gradle",
                ".kotlin",
                "__pycache__",
                "node_modules",
                ".pytest_cache",
            )
        ]
        for name in names:
            found.append(
                os.path.relpath(os.path.join(base, name), ROOT).replace(os.sep, "/")
            )
    return found


def scan(paths, table):
    hits = []
    for rel in paths:
        if rel.startswith(SKIP_PREFIXES):
            continue
        try:
            with open(os.path.join(ROOT, rel), "rb") as handle:
                data = handle.read()
        except (OSError, IsADirectoryError):
            continue
        for name, pattern in table:
            for match in re.finditer(pattern, data):
                line = data[: match.start()].count(b"\n") + 1
                value = match.group(0).decode("utf-8", "replace")
                if len(value) > 100:
                    value = value[:100] + "..."
                hits.append((name, rel, line, value))
    return hits


def show(hits, label):
    print("=== %s: %d ===" % (label, len(hits)))
    for name, rel, line, value in hits:
        print("  [%-22s] %s:%s  %s" % (name, rel, line, value))
    print()


def main(argv):
    everything = "--all" in argv
    secrets = scan(tracked(), SECRETS)
    privacy = scan(tracked(), PRIVACY)
    show(secrets, "secrets in the tracked tree")
    show(privacy, "privacy and posture in the tracked tree")
    if everything:
        local = scan(working_tree(), SECRETS)
        held = [hit for hit in local if hit[1].startswith(LOCAL_ONLY)]
        rest = [hit for hit in local if not hit[1].startswith(LOCAL_ONLY)]
        show(held, "signing material on this machine")
        show(rest, "secrets elsewhere on this machine")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
