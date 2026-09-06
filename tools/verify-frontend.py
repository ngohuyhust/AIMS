#!/usr/bin/env python3
"""Verify the preserved frontend against the read-only source and its manifest."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, default=root.parent / "ISD.20252-25")
args = parser.parse_args()
manifest = json.loads((root / "docs/frontend-manifest.json").read_text())
excluded = {"node_modules", "dist", ".angular", "target", ".DS_Store", "__pycache__"}


def included(path):
    return not (excluded.intersection(path.parts) or path.name == ".env" or path.name.startswith(".env."))


expected = manifest["files"]
tracked = subprocess.check_output(
    ["git", "ls-files", "-z", "src/frontend"], cwd=args.source
).decode().split("\0")
source_paths = {str(Path(p).relative_to("src/frontend")) for p in tracked if p and included(Path(p))}
actual_paths = {
    str(p.relative_to(root / "src/frontend"))
    for p in (root / "src/frontend").rglob("*")
    if p.is_file() and included(p.relative_to(root / "src/frontend"))
}
errors = []
if source_paths != set(expected) or actual_paths != set(expected):
    errors.append("Frontend file sets differ from the recorded source manifest")
for relative, digest in expected.items():
    for label, base in (("source", args.source), ("target", root)):
        path = base / "src/frontend" / relative
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            errors.append(f"Content mismatch: {label}/src/frontend/{relative}")
if errors:
    raise SystemExit("\n".join(errors))
print(f"PASS: {len(expected)} frontend files match source and baseline SHA-256; no added source files.")
