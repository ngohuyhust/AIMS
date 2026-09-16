#!/usr/bin/env python3
"""Fail if default Compose is not the secret-free Supabase Spring/frontend path."""

from pathlib import Path
import os
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
with tempfile.NamedTemporaryFile(mode="w", suffix=".env") as env_file:
    env_file.write("# Empty CI placeholder; runtime secrets are not needed for topology checks.\n")
    env_file.flush()
    environment = dict(os.environ, AIMS_ENV_FILE=env_file.name)
    result = subprocess.run(
        ["docker", "compose", "config", "--services"],
        cwd=ROOT,
        env=environment,
        check=True,
        capture_output=True,
        text=True,
    )
services = result.stdout.split()
if services != ["backend", "frontend"]:
    raise SystemExit(
        f"FAIL: default Compose services are {services}, expected backend and frontend"
    )

compose = (ROOT / "docker-compose.yml").read_text()
compose_required = (
    "${AIMS_ENV_FILE:-.env.supabase}",
    "SPRING_PROFILES_ACTIVE: production",
    "AIMS_DB_SCHEMA: aims_java",
    "VIETQR_ENABLE_TEST_CALLBACK: \"false\"",
    "read_only: true",
    "no-new-privileges:true",
)
compose_missing = [marker for marker in compose_required if marker not in compose]
if compose_missing:
    raise SystemExit(f"FAIL: default Supabase Compose markers are missing: {compose_missing}")
for forbidden in ("postgres:", "AIMS_LOCAL_DB_PASSWORD", "aims_local"):
    if forbidden in compose:
        raise SystemExit(f"FAIL: retired local database marker remains in Compose: {forbidden}")

retired = (
    ROOT / "compose.supabase.yml",
    ROOT / "src/backend/src/main/resources/application-local.yml",
    ROOT / "tools/init-local-env.py",
    ROOT / "tools/verify-supabase-compose.py",
)
remaining = [str(path.relative_to(ROOT)) for path in retired if path.exists()]
if remaining:
    raise SystemExit(f"FAIL: duplicate local/Supabase runtime files remain: {remaining}")

dockerfile = (ROOT / "src/backend/Dockerfile").read_text()
required = (
    "maven:3.9.16-eclipse-temurin-21@sha256:",
    " AS build",
    "./mvnw",
    "COPY --from=build",
)
missing = [marker for marker in required if marker not in dockerfile]
if missing:
    raise SystemExit(f"FAIL: backend Dockerfile is not a self-contained multi-stage build: {missing}")

wrapper = (ROOT / "src/backend/.mvn/wrapper/maven-wrapper.properties").read_text()
if "apache-maven/3.9.16/apache-maven-3.9.16-bin.zip" not in wrapper:
    raise SystemExit("FAIL: Docker builder and Maven Wrapper versions are no longer aligned")

frontend = (ROOT / "src/frontend/Dockerfile").read_text()
frontend_required = (
    "node:24.16.0-slim@sha256:",
    "npm ci",
    "npm run build",
    "nginxinc/nginx-unprivileged:1.29.1-alpine@sha256:",
    "COPY --from=build",
)
frontend_missing = [marker for marker in frontend_required if marker not in frontend]
if frontend_missing:
    raise SystemExit(f"FAIL: frontend production image is incomplete: {frontend_missing}")

print("PASS: default Compose self-builds Spring/frontend against the external Supabase environment.")
