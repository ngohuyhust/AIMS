#!/usr/bin/env python3
"""Verify the secret-free Compose path for the migrated Supabase schema."""

from pathlib import Path
import os
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ROOT / "compose.supabase.yml"

if not COMPOSE.exists():
    raise SystemExit("FAIL: compose.supabase.yml is missing")

with tempfile.NamedTemporaryFile(mode="w", suffix=".env") as env_file:
    env_file.write("# Empty CI placeholder; runtime credentials are never required by this check.\n")
    env_file.flush()
    environment = dict(os.environ, AIMS_SUPABASE_ENV_FILE=env_file.name)
    result = subprocess.run(
        ["docker", "compose", "-f", str(COMPOSE), "config", "--services"],
        cwd=ROOT,
        env=environment,
        check=True,
        capture_output=True,
        text=True,
    )

services = result.stdout.split()
if services != ["backend", "frontend"]:
    raise SystemExit(f"FAIL: Supabase Compose services are {services}, expected backend and frontend")

contents = COMPOSE.read_text()
required = (
    "${AIMS_SUPABASE_ENV_FILE:-.env.supabase}",
    "SPRING_PROFILES_ACTIVE: production",
    "AIMS_DB_SCHEMA: aims_java",
    "VIETQR_ENABLE_TEST_CALLBACK: \"false\"",
    "read_only: true",
    "no-new-privileges:true",
)
missing = [marker for marker in required if marker not in contents]
if missing:
    raise SystemExit(f"FAIL: Supabase Compose safety markers are missing: {missing}")
if "postgres:" in contents or "AIMS_DB_PASSWORD:" in contents:
    raise SystemExit("FAIL: Supabase Compose must not embed a database service or password")

gitignore = (ROOT / ".gitignore").read_text().splitlines()
if ".env.*" not in gitignore:
    raise SystemExit("FAIL: Supabase environment files are not ignored")

print("PASS: Supabase Compose uses only Spring/frontend and a Git-ignored external environment file.")
