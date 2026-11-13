# What this pull request does

One or two sentences. The link to the issue it closes, if there is one.

## Why

The reasoning, not the restatement of the diff. What was wrong, what was measured, and what was
tried and rejected.

## How it was checked

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` is green
- [ ] `python tools/sweep_secrets.py` passes
- [ ] `python tools/prose_gate.py` passes
- [ ] The change was seen on a phone, if it touches anything a JVM test cannot reach

## Notes for the reviewer

Anything that is unfinished on purpose, anything that needs a decision, and the parts you would
rather not read twice.
