# Frontend Compose validation

Historical note: this checkpoint initially included a local PostgreSQL service. The later
Supabase-only runtime checkpoint retained the frontend image but removed that database path.

## Authorized scope

The user authorized completing the default Docker Compose stack with the existing Angular frontend.
This checkpoint changes only frontend container/runtime files, Compose topology, validation and
documentation. Angular TypeScript, HTML, CSS, routes, request bodies, headers and API selection are
unchanged. Already-pushed commits are retained; no history rewrite or force push is permitted.

## Design

- A digest-pinned Node24 builder runs reproducible `npm ci` and the existing production build.
- A digest-pinned unprivileged Nginx runtime receives only `dist/frontend/browser`.
- Nginx exposes port8080, returns `ok` from `/health`, and falls back to `index.html` for Angular
  client routes.
- Compose binds the frontend only to `127.0.0.1:4200`, waits for the backend health check, uses a
  read-only root filesystem plus ephemeral `/tmp`, drops capabilities and enables
  `no-new-privileges`.
- Existing localhost API selection continues to call `http://localhost:3000`; no proxy or Angular
  application edit is needed.

## Verification record

- `docker compose up -d --build --wait`: all three services healthy.
- Frontend `/health`: `ok`; `/` and `/login`: Angular shell; backend actuator: `UP`.
- Runtime inspection: UID101:101, read-only root, all capabilities dropped and
  `no-new-privileges`.
- `docker compose build frontend`: all Dockerfile build/runtime layers cached on repeat.
- `npm test -- --watch=false`: 7/7 tests pass; `npm run build`: pass on Node24.16.0.
- `./mvnw -B verify` in Java21 Docker: 663/663 tests pass with zero failures, errors or skips;
  PostgreSQL17.6 Testcontainers/Flyway V1–V10 and executable JAR pass.
- `python3 tools/verify-compose.py`: pass.
- `python3 tools/verify-frontend.py` with and without `--source-ref`: all70 source baseline files,
  65 unchanged destination files, five approved edits and three approved additions verified.
- `git diff --check`: pass. Final commit hashes and exact remote verification are recorded in
  `MIGRATION_STATUS.md` and the checkpoint report after push.
