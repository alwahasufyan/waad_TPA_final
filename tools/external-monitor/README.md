# WAAD External Monitor

A **standalone** watchdog for WAAD TPA. It runs *outside* the WAAD backend so it
can still alert over Telegram when the backend or frontend is completely down —
something the in-app monitor (MON-1C) cannot do for itself.

## What it does

- Polls, on an interval:
  - Backend health (`WAAD_MONITOR_BACKEND_URL`, e.g. `/actuator/health`)
  - Frontend URL (`WAAD_MONITOR_FRONTEND_URL`)
  - Optionally a DB health URL (`WAAD_MONITOR_DB_HEALTH_URL`)
- Sends **one** Telegram alert per outage, then stays quiet until the
  cooldown passes (no spam).
- Sends **one** recovery message when a target comes back.
- POSTs a heartbeat to the WAAD backend so the admin UI can show that an
  external watchdog is alive (`WAAD_MONITOR_HEARTBEAT_URL`).
- Requires **no login** — it only needs the Telegram credentials and, if you
  set one, the shared heartbeat token.

## Run locally

```bash
cd tools/external-monitor
cp .env.example .env   # fill in TELEGRAM_* and URLs
node --env-file=.env monitor.js
```

## Run with Docker (recommended, as a separate container)

Build and run it on a **different host or at least a different container** from
the app, so an app/host failure does not take the monitor down with it:

```bash
docker build -t waad-external-monitor tools/external-monitor
docker run -d --name waad-external-monitor --env-file tools/external-monitor/.env waad-external-monitor
```

Or as a compose sidecar (illustrative — keep it in its own compose/host ideally):

```yaml
services:
  external-monitor:
    build: ./tools/external-monitor
    env_file: ./tools/external-monitor/.env
    restart: unless-stopped
```

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `WAAD_MONITOR_BACKEND_URL` | `http://localhost:8081/actuator/health` | Backend health check |
| `WAAD_MONITOR_FRONTEND_URL` | `http://localhost:3001` | Frontend reachability |
| `WAAD_MONITOR_DB_HEALTH_URL` | *(empty)* | Optional DB health URL |
| `TELEGRAM_BOT_TOKEN` | *(required)* | Telegram bot token |
| `TELEGRAM_CHAT_ID` | *(required)* | Telegram chat id |
| `TELEGRAM_ALERT_THREAD_ID` | *(empty)* | Optional forum thread id |
| `WAAD_MONITOR_INTERVAL_SECONDS` | `60` | Poll interval (min 10) |
| `WAAD_MONITOR_COOLDOWN_SECONDS` | `900` | Min seconds between repeat alerts |
| `WAAD_MONITOR_TIMEOUT_MS` | `10000` | Per-request timeout |
| `WAAD_MONITOR_ENVIRONMENT` | `production` | Label used in messages |
| `WAAD_MONITOR_HEARTBEAT_URL` | *(empty)* | WAAD heartbeat endpoint |
| `WAAD_MONITOR_HEARTBEAT_TOKEN` | *(empty)* | Must match backend `WAAD_MONITOR_HEARTBEAT_TOKEN` if set |

## Run as a standalone service (compose override)

A dedicated compose file is provided at the repo root: **`compose.monitor.yaml`**.
It is intentionally separate from the main stack so the monitor survives an app outage.

```bash
cd tools/external-monitor
cp .env.example .env         # fill TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, URLs
cd ../..
docker compose -f compose.monitor.yaml up -d --build
docker logs -f waad-external-monitor
```

- **Where do I put the Telegram token?** In `tools/external-monitor/.env`
  (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`). This file is not committed.
- **How do I verify the heartbeat?** Set `WAAD_MONITOR_HEARTBEAT_URL` to
  `<backend>/api/v1/system/monitoring/external-heartbeat`, start the monitor, then open
  **إعدادات النظام → التنبيهات والمراقبة → المراقب الخارجي**; the card shows the last
  heartbeat time and status. It also appears in the DB column
  `system_monitoring_settings.last_external_heartbeat_at`.

The main stack (`compose.yaml` / `compose.local.yaml`) is untouched by this file.

## Tests

```bash
node test/monitor.test.js
```
