"""
Builds the Liminalis resource pack into a distributable zip, and prints the
sha1 the server needs.

Two things worth knowing about resource pack zips, both of which bite people:

  1. pack.mcmeta must be at the ROOT of the archive, not inside a folder.
     Zipping the directory itself (right-click, "Send to compressed folder")
     produces a pack Minecraft silently refuses to load.

  2. The server verifies the sha1 of the exact bytes it downloads. Rebuild the
     pack and the hash changes, so require-resource-pack will reject every
     client until server.properties is updated to match. This script prints the
     new hash every time for exactly that reason.

Run:  python tools/build_pack.py
"""

import hashlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "pack"
DIST = ROOT / "dist"
NAME = "Liminalis-Pack.zip"

# Any fixed date. The value is irrelevant; that it never moves is the point.
FIXED_TIMESTAMP = (2026, 1, 1, 0, 0, 0)

# Never ship these even if they end up in the pack directory.
EXCLUDE_SUFFIXES = {".md", ".xcf", ".psd", ".aseprite"}
EXCLUDE_NAMES = {".DS_Store", "Thumbs.db", "desktop.ini"}


def collect():
    files = []
    for path in sorted(PACK.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix.lower() in EXCLUDE_SUFFIXES or path.name in EXCLUDE_NAMES:
            continue
        files.append(path)
    return files


def build():
    if not (PACK / "pack.mcmeta").is_file():
        raise SystemExit("pack/pack.mcmeta is missing - refusing to build a pack "
                         "Minecraft will not load")

    DIST.mkdir(exist_ok=True)
    target = DIST / NAME
    files = collect()

    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            # Relative to pack/, so pack.mcmeta lands at the archive root.
            name = path.relative_to(PACK).as_posix()

            # Fixed timestamp, so the archive is byte-identical when the content is.
            # Zip entries carry mtimes, so writing them normally changes the sha1 on every
            # rebuild even when nothing was edited - and with require-resource-pack on, a
            # changed hash kicks every client until server.properties is updated. Nobody
            # should have to re-paste a hash because they ran the build twice.
            info = zipfile.ZipInfo(name, date_time=FIXED_TIMESTAMP)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, path.read_bytes())

    digest = hashlib.sha1(target.read_bytes()).hexdigest()
    size_kb = target.stat().st_size / 1024

    print(f"built {target.relative_to(ROOT)}  ({len(files)} files, {size_kb:.1f} KB)")
    print()
    print("sha1: " + digest)
    print()
    print("server.properties:")
    print("  require-resource-pack=true")
    print("  resource-pack=<url you host the zip at>")
    print("  resource-pack-sha1=" + digest)
    print()
    print("The hash changes every rebuild. If clients start being kicked for a")
    print("failed pack, this line is why.")
    return digest


if __name__ == "__main__":
    build()
