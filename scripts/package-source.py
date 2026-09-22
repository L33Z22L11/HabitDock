#!/usr/bin/env python3
"""Export files intended for Git, excluding local builds, SDKs and signing keys."""

from pathlib import Path
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent.parent
files = subprocess.check_output(
    ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root
).decode().split("\0")
output = root / "dist/HabitDock-source.zip"
output.parent.mkdir(exist_ok=True)
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    for name in sorted(set(files) - {""}):
        path = root / name
        if path.is_file() and not path.is_symlink():
            archive.write(path, "HabitDock/" + name)
print(output)
