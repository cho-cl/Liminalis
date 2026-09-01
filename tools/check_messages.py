"""
Cross-checks every message key the code can ask for against messages.yml.

Missing keys are invisible until a player triggers exactly the line that needs
one, and then they see "[missing message: ability.drones.focus.name]" instead
of whatever it was meant to say. That has happened often enough on this project
to be worth a tool rather than a fix each time.

This half checks the keys written out in full in the source. The other half -
keys built at runtime out of a modifier id and a suffix, which are the ones
nobody remembers to write because a new ability adds a dozen at once - is
checked by MessageAudit against the real registry when the server starts. A
regex cannot do that job honestly, so it does not try.

Run:  python tools/check_messages.py
Exit code 1 if anything is missing, so it can gate a build.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "liminalis-plugin" / "src" / "main" / "java"
MESSAGES = ROOT / "liminalis-plugin" / "src" / "main" / "resources" / "messages.yml"


def defined_keys():
    """Every dotted key messages.yml actually provides."""
    keys = set()
    stack = []
    for raw in MESSAGES.read_text(encoding="utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        name = raw.strip().split(":", 1)[0].strip()
        if ":" not in raw:
            continue
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, name))
        path = ".".join(part for _, part in stack)
        # A line with something after the colon is a value; a bare one is a parent.
        if raw.strip().split(":", 1)[1].strip():
            keys.add(path)
    return keys


LITERAL = re.compile(r'messages\.(?:get|send)\(\s*(?:[A-Za-z_][\w.]*\s*,\s*)?"([a-z][\w.\-]*)"')


def literal_keys():
    """Keys written out in full in the source."""
    found = {}
    for path in JAVA.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for match in LITERAL.finditer(text):
            found.setdefault(match.group(1), path.relative_to(ROOT))
    return found


def main():
    defined = defined_keys()
    problems = []

    for key, where in sorted(literal_keys().items()):
        if key in defined:
            continue
        if key.count(".") == 0 or key.endswith("."):
            # A key ending in a dot is a prefix being concatenated with an id at runtime.
            # The startup audit covers those against the real registry; a regex cannot.
            continue
        problems.append((key, str(where)))

    print("messages.yml defines %d keys" % len(defined))
    if not problems:
        print("every literal key in the source is defined")
        return 0

    print()
    print("%d MISSING:" % len(problems))
    for key, where in problems:
        print("  %-52s %s" % (key, where))
    return 1


if __name__ == "__main__":
    sys.exit(main())
