"""The list of files the repository tracks.

The gate has to know which files are published, and the authority on that is git's own index. The
`git` binary is used when it runs.

Where it cannot run, the index is read directly. It is a documented format: four bytes of `DIRC`,
a version, a count, and then one record per file, each with its stat data, its object id and its
path. That is a small enough thing to read that the gate does not need a second program to do it,
and a machine where the git binary is blocked by a policy can still run the gate over its own tree.

    python tools/gitfiles.py            the tracked paths, one per line
    python tools/gitfiles.py --count    how many there are
"""

import os
import struct
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX = os.path.join(ROOT, ".git", "index")


def from_git():
    """The tracked paths, by asking git."""
    out = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True
    ).stdout
    return [p.decode("utf-8", "replace") for p in out.split(b"\0") if p]


def from_index(path=INDEX):
    """The tracked paths, by reading the index."""
    with open(path, "rb") as handle:
        data = handle.read()
    if data[:4] != b"DIRC":
        raise ValueError("not a git index")
    version, count = struct.unpack(">II", data[4:12])
    if version not in (2, 3, 4):
        raise ValueError("index version %d is not read here" % version)
    # A SHA-1 repository writes a 20 byte object id, a SHA-256 one writes 32. The one that lands
    # the reader exactly at the end of the file is the right one.
    for width in (20, 32):
        try:
            return _read(data, version, count, width)
        except (ValueError, IndexError, struct.error):
            continue
    raise ValueError("the index was not read")


def _read(data, version, count, width):
    names = []
    at = 12
    for _ in range(count):
        start = at
        at += 40                      # ten stat fields
        at += width                   # the object id
        flags = struct.unpack(">H", data[at:at + 2])[0]
        at += 2
        if flags & 0x4000 and version >= 3:
            at += 2                   # the extended flags
        length = flags & 0x0FFF
        if length < 0x0FFF:
            name = data[at:at + length]
            at += length + 1         # the name is written with its own terminator
        else:
            end = data.index(b"\0", at)
            name = data[at:end]
            at = end + 1
        while (at - start) % 8 != 0:  # the padding to a multiple of eight
            at += 1
        names.append(name.decode("utf-8", "replace"))
    if at > len(data) - width:
        raise ValueError("the object id is the wrong width")
    # What sits between the last entry and the trailer is git's own extensions: the cached tree,
    # the resolve undo and the like. They are not read here, and the file does not need them.
    return names


def tracked():
    """The tracked paths, from git when it runs and from the index when it does not."""
    try:
        return from_git()
    except (OSError, subprocess.CalledProcessError):
        pass
    return from_index()


def main(argv):
    paths = tracked()
    if "--count" in argv:
        print(len(paths))
    else:
        for path in paths:
            print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
