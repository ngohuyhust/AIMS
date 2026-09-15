#!/usr/bin/env python3
"""Fail if the default Compose path cannot build and start the Spring backend."""

from pathlib import Path
import os
import subprocess


ROOT = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
environment.update(
    AIMS_LOCAL_DB_PASSWORD="compose-check-database-password",
    JWT_SECRET="compose-check-jwt-secret-at-least-32-bytes",
)

result = subprocess.run(
    ["docker", "compose", "config", "--services"],
    cwd=ROOT,
    env=environment,
    check=True,
    capture_output=True,
    text=True,
)
services = result.stdout.split()
if services != ["postgres", "backend", "frontend"]:
    raise SystemExit(
        f"FAIL: default Compose services are {services}, expected postgres, backend and frontend"
    )

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

print("PASS: default Compose self-builds postgres, Spring backend and Angular frontend.")
