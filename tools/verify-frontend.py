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
parser.add_argument("--source-ref", action="store_true", help="Verify baseline from preserved Git history (CI)")
args = parser.parse_args()
manifest = json.loads((root / "docs/frontend-manifest.json").read_text())
approval_path = root / "docs/frontend-approved-changes.json"
approved = json.loads(approval_path.read_text())["files"] if approval_path.exists() else {}
excluded = {"node_modules", "dist", ".angular", "target", ".DS_Store", "__pycache__"}


def included(path):
    return not (excluded.intersection(path.parts) or path.name == ".env" or path.name.startswith(".env."))


expected = manifest["files"]
tracked = subprocess.check_output(
    ["git", "ls-tree", "-r", "--name-only", "-z", manifest["sourceCommit"], "src/frontend"] if args.source_ref
    else ["git", "ls-files", "-z", "src/frontend"], cwd=root if args.source_ref else args.source
).decode().split("\0")
source_paths = {str(Path(p).relative_to("src/frontend")) for p in tracked if p and included(Path(p))}
actual_paths = {
    str(p.relative_to(root / "src/frontend"))
    for p in (root / "src/frontend").rglob("*")
    if p.is_file() and included(p.relative_to(root / "src/frontend"))
}
errors = []
if source_paths != set(expected) or actual_paths != set(expected) | set(approved):
    errors.append("Frontend file sets differ from the recorded source manifest")
for relative, digest in expected.items():
    for label, base in (("source", args.source), ("target", root)):
        path = base / "src/frontend" / relative
        required = approved.get(relative, digest) if label == "target" else digest
        if label == "source" and args.source_ref:
            content = subprocess.check_output(["git", "show", f"{manifest['sourceCommit']}:src/frontend/{relative}"], cwd=root)
        else:
            content = path.read_bytes() if path.is_file() else b""
        if hashlib.sha256(content).hexdigest() != required:
            errors.append(f"Content mismatch: {label}/src/frontend/{relative}")
for relative, digest in approved.items():
    path = root / "src/frontend" / relative
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        errors.append(f"Approved content mismatch: target/src/frontend/{relative}")
if errors:
    raise SystemExit("\n".join(errors))
if approved:
    print(f"PASS: source {len(expected)}/{len(expected)} matches original baseline; target {len(set(expected)-set(approved))} unchanged, {len(set(expected)&set(approved))} approved edits, {len(set(approved)-set(expected))} approved additions; all SHA-256 verified.")
else:
    print(f"PASS: {len(expected)} frontend files match source and baseline SHA-256; no added source files.")
