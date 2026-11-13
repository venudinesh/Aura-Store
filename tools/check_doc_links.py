#!/usr/bin/env python3
"""Do the handoff docs' links resolve, and do their line anchors still name what they cite?

Stdlib only, no arguments: `python tools/check_doc_links.py` from the project root. It reads README.md and
CONTINUATION.md, takes every relative markdown link, checks the file exists and that a `#LNNN` anchor is inside
it, and — where the link's label is a single backticked identifier — checks that the identifier still appears
near that line. Anchors drift because the docs are written against a snapshot of a file that keeps growing, and
a wrong line number in a handoff is worse than no line number.
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINK = re.compile(r"\[([^\]]*)\]\((?!https?:)([^)#\s]+)(#L(\d+))?\)")
IDENTIFIER = re.compile(r"^`([A-Za-z_][A-Za-z0-9_]*)`(?:\([^)]*\))?$")
DOCS = ("README.md", "CONTINUATION.md")

# Both docs quote `·` and non-breaking spaces; the Windows console is cp1252 and raises on them, which
# would stop the report halfway through and make a clean run look like a passing one.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

problems = 0
contents: dict[str, list[str]] = {}


def body(path: str) -> list[str]:
    if path not in contents:
        with open(path, encoding="utf-8", errors="replace") as handle:
            contents[path] = handle.read().splitlines()
    return contents[path]


def report(doc: str, line: int, what: str) -> None:
    global problems
    problems += 1
    print("%s:%d: %s" % (doc, line, what))


# The published repository ships README.md only; the handoff is kept on the machine that wrote it.
# A document that is not in this checkout has no links to check, and its absence is not a broken one.
checked = [doc for doc in DOCS if os.path.isfile(os.path.join(ROOT, doc))]

for doc in checked:
    with open(os.path.join(ROOT, doc), encoding="utf-8", errors="replace") as handle:
        for number, text in enumerate(handle, 1):
            for match in LINK.finditer(text):
                label, rel, _, anchor = match.groups()
                target = os.path.normpath(os.path.join(ROOT, rel.replace("/", os.sep)))
                if not os.path.exists(target):
                    report(doc, number, "%s does not exist" % rel)
                    continue
                if not anchor or os.path.isdir(target):
                    continue
                lines = body(target)
                at = int(anchor)
                if at > len(lines):
                    report(doc, number, "%s#L%s is past the end of a %d-line file" % (rel, at, len(lines)))
                    continue
                name = IDENTIFIER.match(label.strip())
                if not name:
                    continue
                symbol = name.group(1)
                window = "\n".join(lines[max(0, at - 8): min(len(lines), at + 7)])
                if not re.search(r"\b%s\b" % re.escape(symbol), window):
                    hits = [i + 1 for i, row in enumerate(lines) if re.search(r"\b%s\b" % symbol, row)]
                    report(
                        doc,
                        number,
                        "%s#L%s is labelled `%s`, which appears there only at %s"
                        % (rel, at, symbol, ", ".join(map(str, hits[:8])) or "nowhere"),
                    )

print("checked %s: %d problem(s)" % (" and ".join(checked) or "nothing", problems))
sys.exit(1 if problems else 0)
