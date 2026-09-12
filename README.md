# Wallet & P2P Transfer

Spring Boot (Java 17) + PostgreSQL wallet/P2P-transfer service. See [WRITEUP.md](WRITEUP.md) for the
one-page design write-up (data model, mechanism choices, idempotency placement,
consistency-vs-availability, AI directed-vs-decided).

## API

Auth: `Authorization: Bearer <token>` — the token identifies the calling user (minimal by design;
auth sophistication isn't graded in this round).

| Method | Path                     | Notes |
|--------|--------------------------|-------|
| POST   | `/wallets`               | Get-or-create the caller's wallet. `201` if newly created, `200` if it already existed. |
| GET    | `/wallets/{id}`          | Current balance. |
| POST   | `/transfers`             | Body: `{ "from", "to", "amount_paise", "idempotency_key" }`. `200` on completed/declined, `409` on same-key-different-body. |
| GET    | `/transfers/{id}`        | Transfer status. |
| POST   | `/wallets/{id}/deposit`  | **Test-seeding only**, not one of the 4 spec endpoints. `{ "amount_paise" }`. |

`GET /actuator/health` — health check. `GET /actuator/prometheus` — metrics.

## Run locally (one command)

```bash
docker compose up --build
```

This brings up Postgres + the app (Flyway migrates the schema on boot). API at `http://localhost:8080`.

## Run the burst scripts

Requires `bash`, `curl`, `jq`.

```bash
# against local docker-compose:
./scripts/burst.sh

# against a deployed instance:
BASE_URL=https://<your-app>.onrender.com ./scripts/burst.sh
```

Runs, in order:
1. `01-concurrent-get-or-create.sh` — Gate 1 (race-free get-or-create).
2. `02-idempotent-retry-storm.sh` — Gate 2 (exactly-once transfer + 409 on different-body replay).
3. `03-conservation-under-contention.sh` — Gate 3 (conservation, no-overdraft, clean declines).

Each script is also runnable standalone with an optional concurrency argument, e.g.
`./scripts/03-conservation-under-contention.sh 500`.

## Deploy (Render, free tier, ₹0)

1. Push this repo to GitHub.
2. In Render: **New → Blueprint**, point at the repo — `render.yaml` provisions a free web
   service (Docker) and a free managed Postgres and wires them together.
   - Or manually: New → Web Service → Docker (this repo), plus New → PostgreSQL (free), then set
     `DB_HOST` / `DB_PORT` / `DB_NAME` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` on the web
     service from the database's connection info.
3. Once live, logs are visible in the Render dashboard's Logs tab (structured JSON, one line per
   event, `correlationId` field ties a request's events together) — share that page's link, or a
   screen recording of it streaming during `./scripts/burst.sh`.

## Structured logs

Every log line is JSON (`logstash-logback-encoder`) and carries `correlationId`
(`X-Correlation-Id` request header, or generated) plus context fields (`userId`, `walletId`,
`transferId`, `idempotencyKey`) and an `event` field for the domain events: `wallet_created`,
`wallet_fetched_existing`, `transfer_created`, `wallet_debited`, `wallet_credited`,
`transfer_completed`, `transfer_declined_insufficient_funds`, `idempotent_replay_hit`,
`idempotency_conflict`.

## Metrics

`GET /actuator/prometheus` exposes request rate / latency / error rate (Micrometer's standard HTTP
server metrics) plus domain counters: `wallet_transfers_created_total`,
`wallet_transfers_declined_insufficient_funds_total`, `wallet_transfers_idempotent_replays_total`,
`wallet_transfers_idempotency_conflicts_total`, `wallet_wallets_created_total`,
`wallet_wallets_fetched_existing_total`.

## Build/test locally without Docker

```bash
mvn clean package
java -jar target/wallet-transfer.jar
```
