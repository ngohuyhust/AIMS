#!/usr/bin/env python3
"""Create an isolated AIMS schema beside legacy Supabase public data.

This tool is deliberately additive: it never changes public, drops a schema, or
overwrites a nonempty divergent target. Credentials are never printed.
"""

import json
import os
from pathlib import Path
import re
import runpy
import secrets
import subprocess
import time


ROOT = Path(__file__).resolve().parents[1]
helpers = runpy.run_path(str(ROOT / "tools" / "rehearse-legacy-data.py"))
read_env = helpers["read_env"]
pg_env = helpers["pg_env"]
psql = helpers["psql"]
run = helpers["run"]
wait_until = helpers["wait_until"]
TABLES = helpers["TABLES"]
COPY_TABLES = [
    "roles", "users", "users_roles", "user_audit_logs",
    "products", "media", "books", "cds", "cd_tracks", "dvds", "newspapers",
    "product_logs", "orders", "order_items", "delivery_info", "invoices",
    "payment_transactions", "paypal_transactions", "vietqr_transactions",
]
LEGACY_ENV = helpers["LEGACY_ENV"]
IMAGE = os.environ.get("AIMS_REHEARSAL_IMAGE", "aims-backend:local")
SCHEMA = os.environ.get("AIMS_LEGACY_SCHEMA", "aims_java")


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def main():
    if not re.fullmatch(r"[a-z_][a-z0-9_]{0,62}", SCHEMA):
        raise RuntimeError("AIMS_LEGACY_SCHEMA must be a lowercase SQL identifier")
    if SCHEMA == "public":
        raise RuntimeError("Refusing to migrate into legacy public schema")

    legacy = read_env(LEGACY_ENV)
    environment = pg_env(
        legacy["DB_HOST"], legacy["DB_PORT"], legacy["DB_USERNAME"],
        legacy["DB_PASSWORD"], legacy["DB_DATABASE"], "require",
    )
    application = "aims-supabase-check-" + secrets.token_hex(4)
    jdbc = (
        f"jdbc:postgresql://{legacy['DB_HOST']}:{legacy['DB_PORT']}/"
        f"{legacy['DB_DATABASE']}?sslmode=require&prepareThreshold=0"
    )

    def start_application():
        app_environment = dict(
            os.environ,
            AIMS_DB_URL=jdbc,
            AIMS_DB_USERNAME=legacy["DB_USERNAME"],
            AIMS_DB_PASSWORD=legacy["DB_PASSWORD"],
            AIMS_DB_SCHEMA=SCHEMA,
            JWT_SECRET=secrets.token_hex(32),
        )
        run([
            "docker", "run", "-d", "--name", application,
            "-e", "AIMS_DB_URL", "-e", "AIMS_DB_USERNAME", "-e", "AIMS_DB_PASSWORD",
            "-e", "AIMS_DB_SCHEMA", "-e", "JWT_SECRET",
            "-e", "SPRING_PROFILES_ACTIVE=production",
            "-e", "APP_PUBLIC_URL=https://shop.example.test",
            "-e", "NOTIFICATIONS_ENABLED=false", IMAGE,
        ], env=app_environment, stdout=subprocess.DEVNULL)
        wait_until(lambda: subprocess.run(
            ["docker", "exec", application, "wget", "-q", "-O", "/dev/null",
             "http://127.0.0.1:3000/actuator/health"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0)

    try:
        existed = psql(
            environment,
            "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname=" + literal(SCHEMA) + ");",
        ) == "t"
        if not existed:
            psql(environment, f'CREATE SCHEMA "{SCHEMA}";')
            print(f"Created isolated schema {SCHEMA}; legacy public is unchanged")
        else:
            print(f"Using existing isolated schema {SCHEMA}; legacy public is unchanged")

        start_application()
        run(["docker", "rm", "-f", application], stdout=subprocess.DEVNULL)

        columns = {}
        for table in TABLES:
            source_columns = psql(environment, f"""
                SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position)
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name={literal(table)};
                """)
            target_columns = psql(environment, f"""
                SELECT string_agg(quote_ident(column_name),',' ORDER BY ordinal_position)
                FROM information_schema.columns
                WHERE table_schema={literal(SCHEMA)} AND table_name={literal(table)};
                """)
            source_set = set(source_columns.split(","))
            target_set = set(target_columns.split(","))
            if source_set != target_set:
                raise RuntimeError(f"Column mismatch for {table}; refusing data copy")
            columns[table] = target_columns

        nonempty = int(psql(environment, "SELECT " + "+".join(
            f"(SELECT count(*) FROM \"{SCHEMA}\".\"{table}\")"
            for table in TABLES if table != "roles"
        ) + ";"))

        if nonempty == 0:
            statements = [
                "BEGIN ISOLATION LEVEL REPEATABLE READ",
                "SET LOCAL statement_timeout='120s'",
                "TRUNCATE TABLE " + ",".join(
                    f'"{SCHEMA}"."{table}"' for table in TABLES
                ) + " RESTART IDENTITY CASCADE",
            ]
            for table in COPY_TABLES:
                names = columns[table]
                statements.append(
                    f'INSERT INTO "{SCHEMA}"."{table}" ({names}) '
                    f'SELECT {names} FROM public."{table}"'
                )

            serials = json.loads(psql(environment, f"""
                SELECT coalesce(json_agg(json_build_object('table',table_name,'column',column_name)),'[]')
                FROM information_schema.columns
                WHERE table_schema={literal(SCHEMA)} AND column_default LIKE 'nextval(%';
                """))
            for serial in serials:
                table, column = serial["table"], serial["column"]
                statements.append(
                    "SELECT setval(pg_get_serial_sequence(" +
                    literal(f'{SCHEMA}.{table}') + "," + literal(column) + ")," +
                    f'coalesce((SELECT max("{column}") FROM "{SCHEMA}"."{table}"),1),' +
                    f'EXISTS(SELECT 1 FROM "{SCHEMA}"."{table}"))'
                )

            for table in TABLES:
                names = columns[table]
                statements.append(f"""
                    DO $$ BEGIN
                      IF EXISTS((SELECT {names} FROM public."{table}"
                                 EXCEPT ALL SELECT {names} FROM "{SCHEMA}"."{table}"))
                         OR EXISTS((SELECT {names} FROM "{SCHEMA}"."{table}"
                                    EXCEPT ALL SELECT {names} FROM public."{table}")) THEN
                        RAISE EXCEPTION 'Data mismatch for {table}';
                      END IF;
                    END $$
                    """)
            statements.append("COMMIT")
            psql(environment, ";\n".join(statements) + ";")
            print("Copied one repeatable-read snapshot from public")
        else:
            for table in TABLES:
                names = columns[table]
                mismatch = psql(environment, f"""
                    SELECT count(*) FROM (
                      (SELECT {names} FROM public."{table}"
                       EXCEPT ALL SELECT {names} FROM "{SCHEMA}"."{table}")
                      UNION ALL
                      (SELECT {names} FROM "{SCHEMA}"."{table}"
                       EXCEPT ALL SELECT {names} FROM public."{table}")) differences;
                    """)
                if mismatch != "0":
                    raise RuntimeError(
                        f"Existing {SCHEMA} differs at {table}; refusing overwrite"
                    )
            print("Existing target already matches current public data")

        count_sql = "SELECT json_object_agg(t,c) FROM (" + " UNION ALL ".join(
            f"SELECT '{table}' t,count(*) c FROM \"{SCHEMA}\".\"{table}\""
            for table in TABLES
        ) + ") s;"
        counts = json.loads(psql(environment, count_sql))
        start_application()
        catalog = json.loads(subprocess.check_output([
            "docker", "exec", application, "wget", "-q", "-O", "-",
            "http://127.0.0.1:3000/api/products",
        ], text=True, stderr=subprocess.PIPE))
        if not isinstance(catalog, list) or not catalog:
            raise RuntimeError("Migrated catalog API did not return legacy products")
        flyway = psql(
            environment,
            f'SELECT count(*) FROM "{SCHEMA}".flyway_schema_history WHERE success;',
        )
        print(f"PASS: {sum(counts.values())} rows in {SCHEMA}; Flyway migrations: {flyway}")
        print("Counts:", json.dumps(counts, sort_keys=True))
        print("PASS: Java production profile health and catalog API use the isolated Supabase schema")
    finally:
        subprocess.run(
            ["docker", "rm", "-f", application],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )


if __name__ == "__main__":
    main()
