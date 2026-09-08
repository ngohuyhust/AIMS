#!/usr/bin/env python3
"""Clone the legacy public data into an ephemeral Flyway V1-V10 database.

The legacy session is forced read-only. The archive, containers, volume and network
are always removed. Secrets are inherited by Docker and are never printed.
"""

import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
LEGACY_ENV = ROOT.parent / "ISD.20252-25" / "src" / "backend" / ".env"
IMAGE = os.environ.get("AIMS_REHEARSAL_IMAGE", "aims-backend:local")
TABLES = [
    "books", "cd_tracks", "cds", "delivery_info", "dvds", "invoices",
    "media", "newspapers", "order_items", "orders", "payment_transactions",
    "paypal_transactions", "product_logs", "products", "roles",
    "user_audit_logs", "users", "users_roles", "vietqr_transactions",
]


def read_env(path: Path) -> dict[str, str]:
    values = {}
    for raw in path.read_text().splitlines():
        raw = raw.strip()
        if not raw or raw.startswith("#") or "=" not in raw:
            continue
        key, value = raw.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    required = ["DB_HOST", "DB_PORT", "DB_USERNAME", "DB_PASSWORD", "DB_DATABASE"]
    missing = [key for key in required if not values.get(key)]
    if missing:
        raise RuntimeError("Missing legacy database settings: " + ", ".join(missing))
    return values


def run(args, *, env=None, **kwargs):
    return subprocess.run(args, env=env, check=True, **kwargs)


def wait_until(check, seconds=150):
    for _ in range(seconds):
        try:
            if check():
                return
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Container readiness timed out")


def pg_env(host, port, user, password, database, ssl, read_only=False):
    options = "-c statement_timeout=60000"
    if read_only:
        options += " -c default_transaction_read_only=on"
    return dict(
        os.environ,
        PGHOST=host,
        PGPORT=port,
        PGUSER=user,
        PGPASSWORD=password,
        PGDATABASE=database,
        PGSSLMODE=ssl,
        PGOPTIONS=options,
    )


def psql(environment, sql, network=None):
    command = ["docker", "run", "--rm"]
    if network:
        command += ["--network", network]
    command += [
        "-e", "PGHOST", "-e", "PGPORT", "-e", "PGUSER", "-e", "PGPASSWORD",
        "-e", "PGDATABASE", "-e", "PGSSLMODE", "-e", "PGOPTIONS",
        "postgres:17.6-alpine", "psql", "-XAt", "--set", "ON_ERROR_STOP=1",
        "-c", sql,
    ]
    return subprocess.check_output(
        command, env=environment, text=True, stderr=subprocess.PIPE
    ).strip()


def main():
    legacy = read_env(LEGACY_ENV)
    prefix = "aims-rehearsal-" + uuid.uuid4().hex[:8]
    database = prefix + "-db"
    application = prefix + "-app"
    password = secrets.token_hex(24)
    descriptor, archive = tempfile.mkstemp(
        prefix="aims-legacy-", suffix=".dump", dir="/private/tmp"
    )
    os.close(descriptor)
    os.chmod(archive, 0o600)

    target_env = pg_env(database, "5432", "aims", password, "aims", "disable")
    legacy_env = pg_env(
        legacy["DB_HOST"], legacy["DB_PORT"], legacy["DB_USERNAME"],
        legacy["DB_PASSWORD"], legacy["DB_DATABASE"], "require", True,
    )

    def start_application():
        app_env = dict(
            os.environ,
            AIMS_DB_URL=f"jdbc:postgresql://{database}:5432/aims",
            AIMS_DB_USERNAME="aims",
            AIMS_DB_PASSWORD=password,
            JWT_SECRET=secrets.token_hex(32),
        )
        run([
            "docker", "run", "-d", "--name", application, "--network", prefix,
            "-e", "AIMS_DB_URL", "-e", "AIMS_DB_USERNAME", "-e", "AIMS_DB_PASSWORD",
            "-e", "JWT_SECRET", "-e", "SPRING_PROFILES_ACTIVE=production",
            "-e", "APP_PUBLIC_URL=https://shop.example.test",
            "-e", "NOTIFICATIONS_ENABLED=false", IMAGE,
        ], env=app_env, stdout=subprocess.DEVNULL)
        wait_until(lambda: subprocess.run(
            ["docker", "exec", application, "wget", "-q", "-O", "/dev/null",
             "http://127.0.0.1:3000/actuator/health"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0)

    try:
        run(["docker", "network", "create", prefix], stdout=subprocess.DEVNULL)
        database_env = dict(
            os.environ, POSTGRES_PASSWORD=password, POSTGRES_USER="aims", POSTGRES_DB="aims"
        )
        run([
            "docker", "run", "-d", "--name", database, "--network", prefix,
            "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_USER", "-e", "POSTGRES_DB",
            "postgres:17.6-alpine",
        ], env=database_env, stdout=subprocess.DEVNULL)
        wait_until(lambda: subprocess.run(
            ["docker", "exec", database, "pg_isready", "-U", "aims", "-d", "aims"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0)

        start_application()
        run(["docker", "rm", "-f", application], stdout=subprocess.DEVNULL)

        count_sql = "SELECT json_object_agg(t,c) FROM (" + " UNION ALL ".join(
            f"SELECT '{table}' t,count(*) c FROM public.{table}" for table in TABLES
        ) + ") s;"
        source_counts = json.loads(psql(legacy_env, count_sql))
        psql(
            target_env,
            "TRUNCATE TABLE " + ",".join(f"public.{table}" for table in TABLES)
            + " RESTART IDENTITY CASCADE;",
            prefix,
        )

        dump_command = [
            "docker", "run", "--rm", "-e", "PGHOST", "-e", "PGPORT",
            "-e", "PGUSER", "-e", "PGPASSWORD", "-e", "PGDATABASE",
            "-e", "PGSSLMODE", "-e", "PGOPTIONS", "postgres:17.6-alpine",
            "pg_dump", "-Fc", "--data-only", "--no-owner", "--no-privileges",
            "--schema=public",
        ]
        with open(archive, "wb") as output:
            result = subprocess.run(dump_command, env=legacy_env, stdout=output, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError("Legacy pg_dump failed")

        restore_command = [
            "docker", "run", "--rm", "-i", "--network", prefix,
            "-e", "PGHOST", "-e", "PGPORT", "-e", "PGUSER", "-e", "PGPASSWORD",
            "-e", "PGDATABASE", "-e", "PGSSLMODE", "postgres:17.6-alpine",
            "pg_restore", "--data-only", "--disable-triggers", "--no-owner",
            "--no-privileges", "--exit-on-error", "--dbname=aims",
        ]
        with open(archive, "rb") as source:
            result = subprocess.run(
                restore_command, env=target_env, stdin=source,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
        if result.returncode:
            raise RuntimeError("Ephemeral pg_restore failed")

        target_counts = json.loads(psql(target_env, count_sql, prefix))
        if source_counts != target_counts:
            raise RuntimeError("Per-table row counts differ after restore")

        audit_sql = """
        SELECT json_build_object(
          'negative_stock',(SELECT count(*) FROM products WHERE quantity_in_stock<0),
          'invalid_price',(SELECT count(*) FROM products WHERE current_price<0.3*original_value OR current_price>1.5*original_value),
          'orphan_order_items',(SELECT count(*) FROM order_items oi LEFT JOIN orders o ON o.order_id=oi.order_id LEFT JOIN products p ON p.product_id=oi.product_id WHERE o.order_id IS NULL OR p.product_id IS NULL),
          'orphan_payments',(SELECT count(*) FROM payment_transactions p LEFT JOIN orders o ON o.order_id=p.order_id WHERE o.order_id IS NULL),
          'users_without_bcrypt',(SELECT count(*) FROM users WHERE password_hash NOT LIKE '$2%'),
          'orders_without_token',(SELECT count(*) FROM orders WHERE customer_access_token IS NULL),
          'order_statuses',(SELECT json_object_agg(status,n) FROM (SELECT status,count(*) n FROM orders GROUP BY status) s),
          'payment_statuses',(SELECT json_object_agg(status,n) FROM (SELECT status,count(*) n FROM payment_transactions GROUP BY status) s));
        """
        quality = json.loads(psql(target_env, audit_sql, prefix))
        start_application()
        migrations = psql(
            target_env, "SELECT count(*) FROM flyway_schema_history WHERE success;", prefix
        )
        extra = psql(target_env, """
            SELECT json_object_agg(table_name,n) FROM (
              SELECT 'paypal_operations' table_name,count(*) n FROM paypal_operations
              UNION ALL SELECT 'vietqr_receipts',count(*) FROM vietqr_receipts
              UNION ALL SELECT 'order_lifecycle_operations',count(*) FROM order_lifecycle_operations
              UNION ALL SELECT 'notification_outbox',count(*) FROM notification_outbox) s;
            """, prefix)
        print(f"PASS: {sum(source_counts.values())} rows copied across all 19 legacy tables")
        print("Counts:", json.dumps(source_counts, sort_keys=True))
        print("Data quality:", json.dumps(quality, sort_keys=True))
        print("Flyway migrations:", migrations, "New operational tables:", extra)
        print("PASS: migrated clone starts with production profile and health is UP")
    finally:
        for name in [application, database]:
            subprocess.run(
                ["docker", "rm", "-f", "-v", name],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
        subprocess.run(
            ["docker", "network", "rm", prefix],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        try:
            os.remove(archive)
        except FileNotFoundError:
            pass


if __name__ == "__main__":
    main()
