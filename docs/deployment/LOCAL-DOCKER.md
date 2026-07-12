# WAAD Local Docker

This workflow starts the WAAD application on Windows while preserving the existing development database.

## Authoritative Local Database

The only local development database is:

- Container: `waad-postgres-dev`
- Image: `postgres:16`
- Host port: `5433`
- Container port: `5432`
- Database: `tba_waad_system`
- Volume: `waad_pgdata_dev`

The local Compose overlay does not define a `db` service. The backend connects from Docker to the existing database through:

```text
jdbc:postgresql://host.docker.internal:5433/tba_waad_system
```

## First-Time Setup

1. Start Docker Desktop.
2. Confirm `waad-postgres-dev` exists. The script will start it if stopped, but it will not create a replacement database.
3. Generate `.env.local` if it does not exist:

```powershell
.\waad.ps1 init
```

4. Check the environment:

```powershell
.\waad.ps1 doctor
```

5. Start the app:

```powershell
.\waad.ps1 up
```

## Local URLs

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:8081`
- Backend health: `http://localhost:8081/actuator/health`

The backend container listens on `8080` internally and is published to host port `8081`.
The frontend nginx container listens on `80` internally and is published to host port `3001`.

## Safety Guarantees

- `.\waad.ps1 down` stops only the WAAD local application services.
- `.\waad.ps1 down` does not stop `waad-postgres-dev`.
- No command uses `docker compose down -v`.
- No command deletes Docker volumes.
- Local Compose has no `db` service, so it cannot silently create a second local database.

## Common Commands

```powershell
.\waad.ps1 init
.\waad.ps1 doctor
.\waad.ps1 up
.\waad.ps1 down
.\waad.ps1 restart
.\waad.ps1 rebuild
.\waad.ps1 rebuild backend
.\waad.ps1 logs
.\waad.ps1 logs backend
.\waad.ps1 status
.\waad.ps1 health
.\waad.ps1 backup
.\waad.ps1 restore <backup-file>
.\waad.ps1 config
```

Restore requires typing `RESTORE` before it runs.

## Troubleshooting

Run:

```powershell
.\waad.ps1 doctor
```

The doctor command checks Docker, Compose, required files, required environment variables without printing secrets, port usage, the database container state, HTTP health, Git branch/commit, and relevant container status.
