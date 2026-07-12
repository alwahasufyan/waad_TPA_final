# WAAD Docker Commands

## Local Windows Commands

```powershell
.\waad.ps1 init
.\waad.ps1 doctor
.\waad.ps1 up
.\waad.ps1 down
.\waad.ps1 restart
.\waad.ps1 rebuild
.\waad.ps1 rebuild backend
.\waad.ps1 rebuild frontend
.\waad.ps1 logs
.\waad.ps1 logs backend
.\waad.ps1 status
.\waad.ps1 health
.\waad.ps1 backup
.\waad.ps1 restore <backup-file>
.\waad.ps1 config
```

Local `down` preserves `waad-postgres-dev` and all volumes.

## Production Linux Commands

```bash
bash waad.sh init-prod
bash waad.sh doctor
bash waad.sh deploy
bash waad.sh up
bash waad.sh down
bash waad.sh restart
bash waad.sh rebuild
bash waad.sh rebuild backend
bash waad.sh logs
bash waad.sh logs backend
bash waad.sh status
bash waad.sh health
bash waad.sh backup
bash waad.sh restore <backup-file>
bash waad.sh config
```

Production `down` does not pass `-v`; it does not delete volumes.

## Direct Helper Scripts

```bash
bash deployment/backups/backup-postgres.sh local
bash deployment/backups/backup-postgres.sh production
bash deployment/backups/restore-postgres.sh local <backup-file>
bash deployment/backups/restore-postgres.sh production <backup-file>
bash deployment/health/check-health.sh local
bash deployment/health/check-health.sh production
```
