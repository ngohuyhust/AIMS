#!/usr/bin/env python3
"""Create a local-only database password without printing or overwriting it."""
import os
from pathlib import Path
import secrets

destination = Path(__file__).resolve().parents[1] / ".env"
try:
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    print("Existing .env preserved.")
else:
    with os.fdopen(descriptor, "w") as output:
        output.write(f"AIMS_LOCAL_DB_PASSWORD={secrets.token_hex(32)}\n")
    print("Created ignored .env with a random local database password (mode 0600).")
