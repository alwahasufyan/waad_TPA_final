# WAAD Production Deployment

Production deployment is intentionally separate from local development.

Production uses:

- Compose files: `compose.yaml` + `compose.prod.yaml`
- Env file: `.env.production`
- Spring profile: `prod`
- Database URL inside Docker: `jdbc:postgresql://db:5432/tba_waad_system`
- Public entrypoint: nginx/frontend only, ports `80` and `443`

Do not deploy production without explicit approval.

## First-Time VPS Setup

1. Install Docker and the Docker Compose plugin.
2. Clone or copy the WAAD project to the VPS.
3. Generate `.env.production` if it does not exist:

```bash
bash waad.sh init-prod
```

4. Review `.env.production`, save the generated admin password securely, and fill SMTP values if `EMAIL_ENABLED=true`.
5. Place TLS certificates at:

```text
ssl/fullchain.pem
ssl/privkey.pem
```

6. Validate the environment and configuration without printing interpolated secrets:

```bash
bash waad.sh doctor
bash waad.sh config
```

## Required Production Variables

- `POSTGRES_PASSWORD`
- `DB_PASSWORD`
- `JWT_SECRET`
- `ADMIN_DEFAULT_PASSWORD`
- `SPRING_PROFILES_ACTIVE=prod`

When `EMAIL_ENABLED=true`, these are also required:

- `EMAIL_USERNAME`
- `EMAIL_PASSWORD`

For the current single `postgres` database user, `POSTGRES_PASSWORD` and `DB_PASSWORD` must match.

## Deployment

```bash
bash waad.sh deploy
```

The deploy command performs pre-flight checks, validates `.env.production`, rejects obvious default secrets, checks disk space, records the Git commit, backs up PostgreSQL, builds images, starts the database, starts the backend and lets Flyway migrate forward, checks backend health, starts nginx/frontend, checks HTTPS health, and prints container state.

Backend and PostgreSQL are internal-only in production. Only nginx/frontend exposes public ports `80` and `443`.

If you terminate TLS with external system nginx/Certbot instead of Docker nginx, keep WAAD's Docker frontend private behind that proxy and adjust the production Compose ports deliberately. The default supported path mounts Docker nginx certificates from:

```text
ssl/fullchain.pem
ssl/privkey.pem
```

## Database Rules

- Flyway remains enabled.
- `ddl-auto` remains `validate` in production.
- Flyway clean is never run automatically.
- Historical migrations must not be modified.
- The production database volume is persistent and must not be deleted.

## Backup and Restore

Create a production backup:

```bash
bash waad.sh backup
```

Restore a backup:

```bash
bash waad.sh restore deployment/backups/files/<backup-file>.dump
```

Restore requires typing `RESTORE`.

## Health

Production health checks:

- Frontend/nginx: `https://waadapp.ly/health`
- Frontend/nginx www: `https://www.waadapp.ly/health`
- Backend internal health: `http://backend:8080/actuator/health`
- PostgreSQL: `pg_isready`
