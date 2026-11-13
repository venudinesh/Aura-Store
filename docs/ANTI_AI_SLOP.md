# Keeping the prose free of AI tells

Coding agents leave a residue in prose: a vocabulary, a set of sentence templates,
and a habit of decorating text that needed no decoration. This project scores every
maintained document against a list of those patterns, so the residue does not
accumulate unnoticed. The checker is a vendored copy of
[anti-ai-slop](https://github.com/misbahsy/anti-ai-slop), pinned to the commit in
[`UPSTREAM_REVISION`](../tools/anti-ai-slop/UPSTREAM_REVISION). It sits under `tools/`, beside the
gate that calls it, so a clone can run the gate with no agent tooling on the machine.

## What is installed

| Path | Contents |
|---|---|
| [`SKILL.md`](../tools/anti-ai-slop/SKILL.md) | The writing rules and the five-step loop an agent follows while editing |
| `scripts/rules.json` | 37 rules with their severities and the scoring constants |
| `scripts/slopcheck.py` | The scorer: masking, matching, score arithmetic, JSON output |
| `scripts/sanitize.py` | Invisible-character scan and repair, plus typographic normalisation |
| `scripts/grade.py`, `scripts/credentials.py`, `scripts/detect.py` | The optional model review and detector |
| `tests/` | The upstream suite, which also checks that the skill's own docs pass |
| `LICENSE`, `UPSTREAM_REVISION` | The MIT text, and the commit this copy came from |

It needs Python 3.8 or newer and no third-party packages.

## Running the gate

```bash
python tools/prose_gate.py            # every maintained document
python tools/prose_gate.py --strict   # ignore the baseline and demand 90 everywhere
python tools/prose_gate.py --update   # record today's scores as the new floor
python tools/prose_gate.py --json     # machine-readable
```

Exit status 0 means every document held. Status 1 means a regression, or a document
with no baseline entry that sits below 90. The gate also refuses a baseline entry
whose file has been deleted, so the list cannot rot quietly. CI runs the same command
in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), together with the
skill's own test suite.

For a single file, the scripts are worth calling directly:

```bash
python tools/anti-ai-slop/scripts/slopcheck.py docs/ARCHITECTURE.md
python tools/anti-ai-slop/scripts/sanitize.py docs/ARCHITECTURE.md --fix
```

The first prints each finding with its line, rule, matched text and the suggested
fix, then the arithmetic behind the score. The second reports invisible characters and
typographic substitutions, and repairs them in place with `--fix`.

## What it covers

The gate reads the root documents and every file under `docs/`, minus the archive. The archive is a
frozen copy of the round history, and other files cite line ranges inside it, so a style sweep there
would move those citations. Treat the archive as evidence.

## The baseline, and why it exists

The long records lean on the em dash that rule F1 counts, and their value is in the
numbers they keep rather than in their punctuation. Holding the working notes or the
architecture reference to a 90 gate would mean rewriting the record. The baseline
records a floor instead: a document may not score lower than the floor, and may not
gain a major or blocker finding relative to it. The README was rewritten to clear the
gate outright, so its floor is history rather than a target.

A document with no baseline entry has no floor, and must clear the gate on its own.
That is what keeps new writing honest while the older files are cleaned at a slower
pace.

`--update` rewrites `tools/prose_baseline.json` from the current scores. Run it after
an edit that deliberately changed the prose, and read the resulting diff: a floor that
moved down without a reason is the failure mode this check exists to catch.

## Suppressing a finding

A comment at the end of a line suppresses the named rule on that line and the next
one. Alone on its own line, it covers the paragraph below it. `ALL` suppresses every
rule.

```markdown
Our leverage ratio fell to 2.1x. <!-- slop-ignore W1 -->
```

Use it for a term of art that the rule cannot tell from filler. The directive is
invisible in rendered text, so the commit that adds one should say why.

## Changing the rules

`rules.json` holds the entire rule set. Add a word, a phrase or a pattern there and
rerun `python tools/anti-ai-slop/tests/test_slopcheck.py`. One of those tests
runs the skill's own documentation through the linter and expects the clean fixture to
stay at 95 or better, which is what stops a new rule from flagging ordinary English.

Anything added to the vendored copy is a local divergence. Record it in the commit
message, because a later upgrade of the skill will replace the file.

## What the gate cannot see

The linter reads text, not facts. Four things pass it while still being wrong:

- A number, date, name or quote that the draft invented.
- A meaning changed while smoothing a sentence, such as a caveat dropped from a claim.
- Synonym cycling, where one thing is called three names in a paragraph.
- Anything a careful reader would notice and a regular expression would not.

The upstream skill covers those with a second reviewer, its gate 3, and an optional
model panel, its gate 4. Both need a model and an API key, and neither runs here. They
are for a draft that is about to be published, not for a routine edit.

## Local patches to the vendored copy

Two files differ from upstream, and both differences are the same guard.
`slopcheck.py` and `sanitize.py` reconfigure stdout to UTF-8, because the consoles on
this project's Windows machines are cp1252 and the reports carry arrows and dashes that
cp1252 cannot encode. Without the guard the linter raised `UnicodeEncodeError` partway
through a report and printed no score at all. Each patch carries a comment naming this
project, so the difference is visible when the copy is upgraded.
