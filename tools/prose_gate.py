#!/usr/bin/env python3
"""Gate the project's prose with the vendored anti-ai-slop skill.

Stdlib only. Paths resolve from this file, so it runs from any directory:

    python tools/prose_gate.py            # check every maintained document
    python tools/prose_gate.py --strict   # ignore the baseline, demand the gate everywhere
    python tools/prose_gate.py --update   # record today's scores as the new floor
    python tools/prose_gate.py --json     # machine-readable, for another tool

Every document is scored by `tools/anti-ai-slop/scripts/slopcheck.py`. A
document with no entry in `tools/prose_baseline.json` has to clear the skill's own
gate: 90 or better, with nothing above minor severity. A document that was written
before the gate existed keeps the score it was recorded at, so the check answers
"did this edit make the prose worse" for the long evidence files instead of
pretending they were written under rules they predate.

`docs/CONTINUATION_ARCHIVE_2026-11-01.md` is not checked. It is a frozen copy of the
old round history, and README.md cites line ranges inside it, so editing it for style
would move the citations. Everything else under `docs/` is living text.

Exit codes: 0 = every document held, 1 = a regression or a document below the gate,
2 = the skill or a document is missing.
"""

from __future__ import annotations

import argparse
import datetime
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / "tools" / "anti-ai-slop" / "scripts"
BASELINE = ROOT / "tools" / "prose_baseline.json"

ROOT_DOCS = (
    "README.md",
    "CONTINUATION.md",
    "HANDOFF.md",
    "CHANGELOG.md",
    "PROBLEM_ANALYSIS.md",
    "CONTRIBUTING.md",
    "SECURITY.md",
    "CODE_OF_CONDUCT.md",
)
FROZEN = {"docs/CONTINUATION_ARCHIVE_2026-11-01.md"}
SEVERE = ("major", "blocker")

# The engineering record stays on the machine that wrote it: the .gitignore keeps these out of the
# published repository, so a clone of that repository does not carry them. Their absence there is
# not a deletion, and the check for a deleted document below has to know that.
#
# The three working documents sit at the root. The session notes, the archive and the design
# write-ups sit under `docs/`, next to the four guides that are published, so the rule there is a
# prefix rather than a name: a session note is written per session and the list would otherwise
# grow every time one is finished.
LOCAL_ONLY = ("CONTINUATION.md", "HANDOFF.md", "PROBLEM_ANALYSIS.md")
LOCAL_ONLY_PREFIXES = (
    "docs/CONTINUATION",
    "docs/SESSION_",
)
LOCAL_ONLY_FILES = (
    "docs/AUDIT_AND_FIXES.md",
    "docs/NAVBAR_SETTINGS.md",
    "docs/PORTING_LIQUID_GLASS.md",
)


def is_local_only(name: str) -> bool:
    """Whether a document is kept out of the published repository on purpose."""
    return (
        name in LOCAL_ONLY
        or name in LOCAL_ONLY_FILES
        or name.startswith(LOCAL_ONLY_PREFIXES)
    )

# The Windows console is cp1252 and raises on the grade marks and quotes the report
# carries. check_doc_links.py guards itself the same way.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def documents() -> list[str]:
    found = [name for name in ROOT_DOCS if (ROOT / name).is_file()]
    found += sorted(p.relative_to(ROOT).as_posix() for p in (ROOT / "docs").glob("*.md"))
    return [name for name in found if name not in FROZEN]


def load_linter():
    sys.path.insert(0, str(SCRIPTS))
    import slopcheck  # noqa: E402  (needs the path insert above)

    return slopcheck


def measure(linter, document: str):
    path = ROOT / document
    if not path.is_file():
        return None, [], "missing"
    text = path.read_text(encoding="utf-8", errors="replace")
    result = linter.analyze(text)
    severe = [f for f in result["findings"] if f["severity"] in SEVERE]
    return result, severe, None


def judge(result, severe, floor, strict: bool) -> tuple[bool, str]:
    if strict or floor is None:
        return result["passed"], "gate" if result["passed"] else "below gate"
    if result["score"] < floor["score"] - 0.05:
        return False, "score fell"
    if len(severe) > floor["blocking"]:
        return False, "new blocking"
    return True, "held"


def main() -> int:
    parser = argparse.ArgumentParser(description="Score the project's prose against the anti-ai-slop rules.")
    parser.add_argument("--strict", action="store_true", help="ignore the baseline and demand the skill's gate")
    parser.add_argument("--update", action="store_true", help="record current scores as the new floor")
    parser.add_argument("--json", action="store_true", help="emit JSON instead of a table")
    args = parser.parse_args()

    if not (SCRIPTS / "slopcheck.py").is_file():
        print("The skill is missing from tools/anti-ai-slop. See docs/ANTI_AI_SLOP.md.")
        return 2

    linter = load_linter()
    baseline = {"docs": {}}
    if BASELINE.is_file():
        baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    recorded = baseline.get("docs", {})

    docs = documents()
    rows = []
    problems = []

    for document in docs:
        result, severe, missing = measure(linter, document)
        if missing:
            problems.append("%s: listed as a document but not on disk" % document)
            continue
        floor = None if args.strict else recorded.get(document)
        ok, why = judge(result, severe, floor, args.strict)
        rows.append({
            "document": document,
            "score": result["score"],
            "grade": result["grade"],
            "findings": len(result["findings"]),
            "blocking": len(severe),
            "floor": None if floor is None else floor["score"],
            "ok": ok,
            "verdict": why,
            "worst": [f for f in result["findings"] if f["severity"] in SEVERE][:5],
        })
        if not ok:
            problems.append("%s: %s" % (document, why))

    for gone in sorted(set(recorded) - set(docs)):
        # A clone of the published repository carries README.md and no `docs/` directory: the
        # engineering record stays on the machine that wrote it. An entry whose directory is absent
        # was never shipped, so it is not a deletion. One whose directory is here and whose file is
        # not has been deleted, and that is worth failing on. The three working documents are kept
        # out by the .gitignore on purpose, so they are never shipped either.
        if is_local_only(gone):
            continue
        if not (ROOT / gone).parent.is_dir():
            continue
        problems.append("%s: in the baseline but no longer a document" % gone)

    if args.update:
        BASELINE.write_text(
            json.dumps(
                {
                    "recorded": datetime.date.today().isoformat(),
                    "docs": {
                        row["document"]: {"score": row["score"], "blocking": row["blocking"]}
                        for row in rows
                    },
                },
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
        if args.json:
            print(json.dumps({"updated": [row["document"] for row in rows]}, indent=2))
        else:
            print("Recorded %d document(s) in %s." % (len(rows), BASELINE.relative_to(ROOT).as_posix()))
            for row in rows:
                print("  %-38s %5.1f %-3s blocking=%d" % (row["document"], row["score"], row["grade"], row["blocking"]))
        return 0

    if args.json:
        print(json.dumps({"documents": rows, "problems": len(problems)}, indent=2))
    else:
        print("%-38s %5s %3s %8s %8s %6s  %s" % ("document", "score", "gr", "findings", "blocking", "floor", "verdict"))
        for row in rows:
            floor_text = "gate" if row["floor"] is None else "%.1f" % row["floor"]
            print("%-38s %5.1f %3s %8d %8d %6s  %s" % (
                row["document"], row["score"], row["grade"], row["findings"],
                row["blocking"], floor_text, row["verdict"],
            ))
        if problems:
            print("")
            for row in rows:
                if row["ok"]:
                    continue
                for finding in row["worst"]:
                    print("  %s:%d [%s] %s (%s)" % (
                        row["document"], finding["line"], finding["rule"],
                        finding["match"], finding["severity"],
                    ))
            print("")
            print("%d problem(s). Fix the prose, or record a deliberate change with --update." % len(problems))
        else:
            print("")
            print("%d document(s) held." % len(rows))

    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
