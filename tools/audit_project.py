"""Read the whole tree, not the last diff: what a rereading misses and a checker does not.

`python tools/audit_project.py` from the project root, after a build. Its sections are the `section()`
calls below and the `actual sections run` line reports how many executed; both handoff docs quote that
count, and the last check fails the run when the number written in the prose stops matching the number
of sections that ran. It exits non-zero when a finding needs a decision, so it belongs in the gate rather
than in a round.

Stdlib only. It reads `app/build/test-results/**` and `app/build/reports/lint-results-debug.xml`,
so run the gradle gate first; on a fresh clone those two are what make it say nothing.
"""
import glob
import hashlib
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# The Windows console is cp1252 and dies on the `·` and `≈` this report prints.
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def rd(p):
    return open(os.path.join(ROOT, p), encoding='utf-8', errors='replace').read()

KT = sorted(x.replace(chr(92), '/') for x in glob.glob('app/src/**/*.kt', recursive=True))
SRC = {p: rd(p) for p in KT}
XMLS = sorted(x.replace(chr(92), '/') for x in glob.glob('app/src/main/res/**/*.xml', recursive=True))

report = []
notes = []
counts = {'sections': 0}


def note(line):
    """An echo of a measured fact, not a finding: printed, never counted."""
    notes.append(line)


def section(t):
    counts['sections'] += 1
    report.append('\n=== ' + t + ' ' + '=' * max(0, 60 - len(t)))

# ---------------------------------------------------------------- 1. unused imports
section('unused imports')
OPERATORS = {'getValue', 'setValue', 'provideDelegate'}
for p, s in SRC.items():
    lines = s.split('\n')
    body = '\n'.join(l for l in lines if not l.startswith('import '))
    for line in lines:
        if not line.startswith('import '):
            continue
        sym = line.rstrip().split('.')[-1]
        if re.search(r'\b' + re.escape(sym) + r'\b', body):
            continue
        tag = ' (operator, likely fine)' if sym in OPERATORS else ''
        # `by mutableStateOf(...)` needs the delegate operators imported without naming them, so this
        # is an echo to eyeball once, not something to fix.
        (note if tag else report.append)(f'  {p}: {sym}{tag}')

# ---------------------------------------------------------------- 2. unreferenced resources
section('unreferenced resources (drawable/color/dimen/anim/xml)')
# A resource is often named only by another resource — @color/store_background appears once, inside a
# <style> item, and nowhere in Kotlin. So the corpus is the sources, the manifest and every res file.
all_src = (
    '\n'.join(SRC.values())
    + rd('app/src/main/AndroidManifest.xml')
    + '\n'.join(rd(x) for x in XMLS)
)
for x in XMLS:
    if '/values' in x or '/mipmap' in x:
        continue
    name = os.path.basename(x).rsplit('.', 1)[0]
    kind = x.split('/')[3]  # res/<kind>/...
    if kind in ('menu', 'xml', 'drawable', 'anim', 'animator'):
        if f'@{kind}/{name}' not in all_src and f'R.{kind}.{name}' not in all_src:
            report.append(f'  {x}  ({kind}/{name})')

section('unreferenced values entries')
values = '\n'.join(rd(x) for x in XMLS if '/values' in x)
for m in re.finditer(r'<(color|dimen|style|string-array)\s+name="([^"]+)"', values):
    kind, name = m.group(1), m.group(2)
    if f'R.{kind}.{name}' not in all_src and f'@{kind}/{name}' not in all_src and f'@{kind}/{name.replace(".", "_")}' not in all_src:
        report.append(f'  {kind}/{name}')

# ---------------------------------------------------------------- 3. doc cross-references
section('doc cross-references (§N.M items that do not exist)')
# The round history moved out of the handoff when CONTINUATION.md became a short "what is
# left" list; every evidence file it cites still has to count as cited.
def rdoc(p):
    """Read a document that may not be in this checkout. README.md ships; the rest stays local."""
    return rd(p) if os.path.isfile(os.path.join(ROOT, p)) else ''


docs = {'README.md': rdoc('README.md'), 'CONTINUATION.md': rdoc('CONTINUATION.md'),
        'docs/CONTINUATION_ARCHIVE_2026-11-01.md': rdoc('docs/CONTINUATION_ARCHIVE_2026-11-01.md')}
cont = docs['CONTINUATION.md']
archive = docs['docs/CONTINUATION_ARCHIVE_2026-11-01.md']


def heading_at(text, pos):
    """The `## ` heading the passage at `pos` sits under — both docs keep per-round sections."""
    hit = text.rfind('\n## ', 0, pos)
    return text[hit:hit + 120] if hit >= 0 else ''
# Two letters, not one: the round sections ran out of single suffixes at §3z and went to `aa`, and a
# checker that read only one let a whole round cite §3gg and §4zg for sections that were never written.
known_sections = set(re.findall(r'^## (\d+[a-z]{0,2})\.', cont + archive, re.M))
known_items = set(re.findall(r'^(\d+)\. \*\*', cont + archive, re.M))

# The header's "§3xx is the newest work" pointer is the one line a reader trusts to start at the end of
# the file. It is prose, so it goes stale the way the round sections do not — they are titled by their
# own number and cannot lie about which is last.
newest = re.search(r'§(3[a-z]{0,2}) is the newest work', cont)
if newest:
    highest = max(re.findall(r'^## (3[a-z]{0,2})\.', cont, re.M), key=lambda s: (len(s), s))
    if newest.group(1) != highest:
        report.append(f'  CONTINUATION.md: calls §{newest.group(1)} the newest work; §{highest} is')


def mask_code(text):
    """Blank every backticked span, same length, so offsets still line up.

    A `§4a` inside backticks is the prose *naming* a broken reference — which is what §"Twenty-sixth
    round" does when it records one — not a citation of it. Only a bare `§4a` claims a section exists.
    """
    return re.sub(r'`[^`\n]*`', lambda m: ' ' * len(m.group(0)), text)


for doc, text in docs.items():
    prose = mask_code(text)
    for m in re.finditer(r'§(5|6|7)\.(\d+)', prose):
        if m.group(2) not in known_items:
            report.append(f'  {doc}: §{m.group(1)}.{m.group(2)} has no such item')
    for m in re.finditer(r'§(\d[a-z]{0,2})\b', prose):
        if m.group(1) not in known_sections:
            report.append(f'  {doc}: §{m.group(1)} does not exist')

# ---------------------------------------------------------------- 4. numbers in prose
section('verifiable numbers')
t = f = c = 0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    x = ET.parse(p).getroot()
    c += 1
    t += int(x.get('tests'))
    f += int(x.get('failures'))
lint_path = Path('app/build/reports/lint-results-debug.xml')
# The lint report only exists after a build on this machine. A clone of the published repository has
# no build directory, and the audit there is about the tree, not about a run that was never made.
if lint_path.is_file():
    lint = ET.parse(lint_path).getroot()
    sev = [i.get('severity') for i in lint.iter('issue')]
else:
    sev = []
strings = len(re.findall(r'<string name=', values)) + len(re.findall(r'<plurals name=', values))
note(f'  actual: JVM classes={c} tests={t} failures={f}')
note(f'  actual: lint errors={sev.count("Error")} warnings={sev.count("Warning")} hints={sev.count("Hint")}')
note(f'  actual: strings.xml named entries={strings}')
for doc, text in docs.items():
    for m in re.finditer(r'\b(2[0-9]{2}|3[0-9]{2})\s+(?:JVM )?(tests?|named entries|entries)', text):
        n = int(m.group(1))
        if n in (t, strings):
            continue
        # A figure under `## Twenty-first round:` is that round's total — the per-round sections are the
        # whole shape of both docs. So is a figure the sentence itself scopes ("reached 345 … there",
        # "219, up from 210"). Only an unscoped number outside a round section claims to be today's.
        sentence = text[max(0, text.rfind('.', 0, m.start())):text.find('.', m.end()) + 1]
        scoped = bool(re.search(r'reached|up from|as of|§\d', sentence))
        if scoped or re.search(r'round', heading_at(text, m.start()), re.I):
            continue
        report.append(f'  {doc}: claims "{m.group(0).strip()}" outside any round section, '
                      f'but tests={t} entries={strings}')

# ---------------------------------------------------------------- 4b. instrumented suite
section('instrumented suite vs what the docs say')
INSTR = sorted(x.replace(chr(92), '/') for x in glob.glob('app/src/androidTest/**/*.kt', recursive=True))
instrumented = 0
per_class = set()
for p in INSTR:
    n = len(re.findall(r'@Test\b', rd(p)))
    instrumented += n
    per_class.add(n)
    note(f'  {p}: {n} @Test')
note(f'  actual instrumented total = {instrumented}')
WORDS = {'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5, 'six': 6, 'seven': 7, 'eight': 8,
         'nine': 9, 'ten': 10, 'eleven': 11, 'twelve': 12, 'thirteen': 13, 'fourteen': 14,
         'fifteen': 15, 'sixteen': 16, 'seventeen': 17, 'eighteen': 18, 'nineteen': 19, 'twenty': 20}
for doc, text in docs.items():
    for m in re.finditer(r'[^.]*\b(?:\d{1,3}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|'
                         r'twelve|thirteen|fourteen|fifteen)\b[^.]*instrumented[^.]*\.', text):
        sentence = m.group(0).strip().replace('\n', ' ')
        context = sentence + ' ' + heading_at(text, m.start())
        nums = [int(g) for g in re.findall(r'\b(\d{1,3})\b', sentence)]
        nums += [WORDS[g] for g in re.findall(
            r'\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|'
            r'fifteen)\b', sentence) if g in WORDS]
        # A sentence inside a `## …round` section quotes that round's total on purpose, and a number
        # that matches one class is that class's count rather than the suite's; only the rest can be
        # stale without saying so.
        historical = bool(re.search(r'(twenty|nineteen|eighteen|seventeenth|round|§)', context, re.I))
        if not set(nums) & (per_class | {instrumented}) and not historical:
            report.append(f'  {doc}: "{sentence[:110]}" names no current total ({instrumented}) '
                          f'and sits outside any round section')
    for m in re.finditer(r'these (\w{3,9}) (?:plus|tests)', text):
        w = m.group(1).lower()
        if w in WORDS and WORDS[w] != instrumented:
            report.append(f'  {doc}: "these {w}" but the instrumented suite has {instrumented}')

# ---------------------------------------------------------------- 4c. media that nothing cites
section('media files no document cites')
# A file is cited only when either doc spells its basename. The compact `verify_redmi/119`–`122`
# shorthand deliberately does not count: it names a run's ends, so the middle of a run would go
# unclaimed, and a number in prose ("120 kB") would claim a file it never meant.
all_text = docs['README.md'] + '\n' + docs['CONTINUATION.md'] + '\n' + archive + '\n' + '\n'.join(
    rd(f.replace(chr(92), '/')) for f in glob.glob('docs/*.md')
)
MEDIA_DIRS = ('verify_redmi', 'verify_play', 'verify_realme', 'logo')
listed = []
for d in MEDIA_DIRS:
    if not os.path.isdir(os.path.join(ROOT, d)):
        continue
    for f in sorted(os.listdir(os.path.join(ROOT, d))):
        listed.append((d, f))
    note(f'  {d}/: {len(os.listdir(os.path.join(ROOT, d)))} files')
for d, f in listed:
    if f not in all_text:
        if d.startswith('verify_'):
            note(f'    uncited local capture: {d}/{f}')
        else:
            report.append(f'    uncited: {d}/{f}')
# Shorthand ambiguity: an id used as `verify_x/NNN` that matches more than one file resolves to nothing.
for m in sorted(set(re.findall(r'verify_(\w+)/(\d{3})\b(?!_)', all_text))):
    d, n = 'verify_' + m[0], m[1]
    if not os.path.isdir(os.path.join(ROOT, d)):
        continue
    matches = [f for f in sorted(os.listdir(os.path.join(ROOT, d))) if f.startswith(n + '_')]
    if len(matches) != 1:
        report.append(f'    AMBIGUOUS shorthand {d}/{m[1]} -> {matches}')

# ---------------------------------------------------------------- 5. test suite integrity
section('test suite integrity')
for p in KT:
    if '/test/' not in p and '/androidTest/' not in p:
        continue
    s = SRC[p]
    tests = re.findall(r'@Test\n\s+fun (\w+)', s)
    if not tests:
        continue
    body = s[s.find('@Test'):]
    if 'assert' not in body.lower() and 'Assert' not in s and 'expect' not in body.lower():
        report.append(f'  {p}: {len(tests)} tests, no assertion call found')
for p in KT:
    for tag in ('@Ignore', 'assert(true)', 'assertTrue(true)'):
        if tag in SRC[p]:
            report.append(f'  {p}: contains {tag}')

# ---------------------------------------------------------------- 5b. the ledger of test classes
section('the ledger of test classes against the build')
# README's "| Unit tests | pass" row enumerates every test class with its count. It is the one place a
# reader can see what the suite covers without opening the build directory, and it is hand-maintained,
# so it drifts: a class can go unnamed, a count can go stale, and the list can stop adding up.
per_class = {}
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    x = ET.parse(p).getroot()
    per_class[x.get('name').rsplit('.', 1)[-1]] = int(x.get('tests'))
ledger = next((l for l in docs['README.md'].split('\n') if l.startswith('| Unit tests | pass')), '')
if not ledger:
    m = re.search(r'(\d+)\s+JVM tests across (\d+)\s+classes', docs['README.md'])
    if m:
        c_claimed, cl_claimed = int(m.group(1)), int(m.group(2))
        total_tests = sum(per_class.values())
        total_classes = len(per_class)
        if c_claimed != total_tests or cl_claimed != total_classes:
            report.append(f'  README.md: claims {c_claimed} tests across {cl_claimed} classes, but build runs {total_tests} tests in {total_classes} classes')
    else:
        report.append('  README.md has no "| Unit tests | pass" row or test suite summary to check')
else:
    named = {m.group(1): int(m.group(2)) for m in re.finditer(r'`(\w*Test)` (\d+)', ledger)}
    for name in sorted(per_class):
        if name not in named:
            report.append(f'  {name} runs {per_class[name]} tests and the ledger does not name it')
        elif named[name] != per_class[name]:
            report.append(f'  {name}: the ledger says {named[name]}, the build says {per_class[name]}')
    for name in sorted(set(named) - set(per_class)):
        report.append(f'  the ledger names {name}, which the build does not run')
    added = sum(named.get(n, 0) for n in per_class)
    claimed_total = re.search(r'\*\*(\d+)/\d+, 0 failures\*\*', ledger)
    if not claimed_total:
        report.append('  the ledger row no longer opens with "**N/N, 0 failures**"')
    elif added != int(claimed_total.group(1)):
        report.append(f'  the ledger adds to {added}, the row claims {claimed_total.group(1)}')

# ---------------------------------------------------------------- 6. build config
section('build config')
g = rd('app/build.gradle.kts')
for probe, want in [
    (r'isMinifyEnabled = (\w+)', 'minify'),
    (r'versionCode = (\d+)', 'versionCode'),
    (r'targetSdk = (\d+)', 'targetSdk'),
    (r'minSdk = (\d+)', 'minSdk'),
    (r'compileSdk = (\d+)', 'compileSdk'),
]:
    m = re.search(probe, g)
    note(f'  {want} = {m.group(1) if m else "MISSING"}')
note(f'  proguard-rules.pro exists: {os.path.exists(os.path.join(ROOT, "app/proguard-rules.pro"))}')
note(f'  keystore exists: {os.path.exists(os.path.join(ROOT, "app/keystore/aurastore-release.jks"))}')
gi = rd('.gitignore')
note(f'  .gitignore covers keystore: {"keystore" in gi}, local.properties: {"local.properties" in gi}')

# ---------------------------------------------------------------- 7. dead private members
section('private members the tree never reads')
whole = '\n'.join(SRC.values())
for p, s in SRC.items():
    for m in re.finditer(r'^\s*(private\s+)?(?:@\w+\s*\n\s*)?(private\s+)?(fun|val|var|object|class)\s+(\w+)', s, re.M):
        name = m.group(4)
        if not (m.group(1) or m.group(2)):
            continue
        if len(re.findall(r'\b' + re.escape(name) + r'\b', whole)) <= 1:
            report.append(f'  {p}: private {m.group(3)} {name} is referenced nowhere else')

# ---------------------------------------------------------------- 3b. paths named in prose
section('paths the prose names that do not exist')
# `check_doc_links.py` only sees markdown links; a backticked path in a sentence is the same kind of
# claim and drifts the same way. Checked relative to this project and to its parent, which is where the
# pre-rename backups live.
PARENT = os.path.dirname(ROOT)
for doc, text in docs.items():
    for m in re.finditer(r'`((?:app/|tools/|verify_|logo/|\.prerename|gradle/)[\w./-]*\w)`', text):
        rel = m.group(1)
        # `verify_redmi/119` is the docs' shorthand for a screenshot id, not a path — the media check
        # above is what resolves those, and resolves an id that names two files as a finding.
        if re.fullmatch(r'verify_\w+/\d+', rel):
            continue
        if '*' in rel or rel.endswith(('/', '.')) or '…' in rel:
            continue
        if os.path.exists(os.path.join(ROOT, rel)) or os.path.exists(os.path.join(PARENT, rel)):
            continue
        # §7's traps describe files a failed attempt left behind and that were then removed; a sentence
        # that says so is not claiming the path exists today.
        sentence = text[max(0, text.rfind('.', 0, m.start())):text.find('.', m.end()) + 200]
        if re.search(r'deleted|no longer|not in the tree|was written|used to (be|exist)', sentence, re.I):
            continue
        report.append(f'  {doc}: `{rel}` exists in neither {os.path.basename(ROOT)}/ nor its parent')

# Two ids for one frame is how `109_uninstall_sheet.png` came to be cited as a sheet while showing a row:
# a capture renamed by copy keeps the old pixels and promises new ones.
seen = {}
for d, f in listed:
    if not f.lower().endswith(('.png', '.jpg')):
        continue
    with open(os.path.join(ROOT, d, f), 'rb') as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    seen.setdefault(digest, []).append(f'{d}/{f}')
for names in sorted(seen.values()):
    if len(names) > 1:
        # Adjudicated, not ignored: README §"The evidence that carries no citation" names this pair as the
        # one file in the tree whose name is known wrong, and the three rows that cited it as the sheet now
        # cite 110. A new duplicate is still a finding.
        adjudicated = (
            ['verify_redmi/108_held_after_restart.png', 'verify_redmi/109_uninstall_sheet.png'],
            # Round 45's pause frames are identical *on purpose*: "two frames half a second apart are
            # pixel-identical, 0 changed pixels over the whole screen" is the claim the pair is here to
            # make. A pause that froze the ring is what those two files are evidence of.
            ['verify_redmi/318_round45_paused_a.png', 'verify_redmi/319_round45_paused_b.png'],
        )
        if sorted(names) in [sorted(pair) for pair in adjudicated]:
            note(f'  adjudicated duplicate frame: {names}')
            continue
        report.append(f'    IDENTICAL FRAMES: {names}')
# And an id used by two different files makes the docs' `verify_x/NNN` shorthand ambiguous.
for d in MEDIA_DIRS:
    if not os.path.isdir(os.path.join(ROOT, d)):
        continue
    byid = {}
    for f in sorted(os.listdir(os.path.join(ROOT, d))):
        m = re.match(r'(\d+)_', f)
        if m:
            byid.setdefault(m.group(1), []).append(f)
    for n, files in sorted(byid.items()):
        if len(files) > 1:
            note(f'  id {n} in {d}/ names {len(files)} files: {files}')

# ---------------------------------------------------------------- 8. plural call sites
section('plurals whose placeholders never get filled')
# `pluralStringResource(id, quantity)` substitutes nothing: the quantity picks the arm, and the
# `%1$d` in the text is only replaced by arguments after it. The brand bar shipped that way and read
# "Downloads, %1$d active" to a screen reader until the twenty-seventh pass read it out of a dump.
plural_arms = {}
for m in re.finditer(r'<plurals name="([a-z0-9_]+)">(.*?)</plurals>', values, re.S):
    body = m.group(2)
    other = re.search(r'<item quantity="other">(.*?)</item>', body, re.S)
    text = other.group(1) if other else body
    idx = [int(g) for g in re.findall(r'%(\d+)\$', text)]
    plural_arms[m.group(1)] = max(idx) if idx else (1 if re.search(r'%[ds]', text) else 0)

for p, s in SRC.items():
    for m in re.finditer(r'R\.plurals\.(\w+)', s):
        name = m.group(1)
        need = plural_arms.get(name, 0)
        if need == 0:
            continue
        open_paren = s.rfind('(', 0, m.start())
        if open_paren < 0:
            continue
        depth, args, cur = 0, [], ''
        for ch in s[open_paren + 1:]:
            if ch in '([':
                depth += 1
            elif ch in ')]':
                if depth == 0:
                    break
                depth -= 1
            if ch == ',' and depth == 0:
                args.append(cur)
                cur = ''
            else:
                cur += ch
        if cur.strip():
            args.append(cur)
        # args[0] is the `R.plurals.X` expression itself, args[1] the quantity; only the rest can fill
        # a placeholder.
        given = len(args) - 2
        if given < need and not any('*'.strip() in a for a in args[1:]):
            report.append(f'  {p}: R.plurals.{name} needs {need} format arg(s), call passes {given}')

# ------------------------------------ last: the checker's own number in the prose, against the final count
section('the section count the docs quote')
NUMBER_WORDS = {4: 'four', 5: 'five', 6: 'six', 7: 'seven', 8: 'eight', 9: 'nine', 10: 'ten', 11: 'eleven',
                12: 'twelve', 13: 'thirteen', 14: 'fourteen', 15: 'fifteen', 16: 'sixteen'}
# This one has to run last: reading `counts['sections']` mid-flight makes it agree with a partial total,
# which is how an earlier draft passed a docs claim that was already stale.
wanted = NUMBER_WORDS.get(counts['sections'], str(counts['sections']))
for doc, text in docs.items():
    for m in re.finditer(r'\b(Eleven|Ten|Twelve|Thirteen|Four|Five|Six|Seven|Eight|Nine|Fourteen|Fifteen'
                         r'|Sixteen) (sections|checks|questions)\b', text):
        if m.group(1).lower() != wanted:
            report.append(f'  {doc}: says "{m.group(0)}" but {counts["sections"]} sections ran '
                          f'("{wanted}")')

# Computed last: every section above has had its chance to append a finding, and the section count the
# docs are checked against has to include them all.
note(f'  actual sections run = {counts["sections"]}')
findings = [line for line in report if not line.startswith('\n===')]
print('\n'.join(report))
print('\n'.join(notes))
print(f'\n{len(findings)} finding(s) needing a decision.')
sys.exit(1 if findings else 0)
