# Classification Engine Docker Runtime — Final Verification Report

**Date:** 2026-07-12 · **Branch:** `main` @ `c98f463` (synced with `origin/main`, `0 0`)
**Verified from the committed `main` branch only** (down → rebuild backend+frontend → up → doctor/health + engine + MC-4C tests).

---

## 1. Blocker found & fixed
`backend/docker-entrypoint.sh` was committed with **CRLF** line endings → its `#!/bin/sh` shebang resolved to `/bin/sh\r`, so the backend container failed at startup with `exec /app/docker-entrypoint.sh: no such file or directory` and the whole stack was unhealthy.
**Fix (committed to main, `c98f463`):** converted the entrypoint (and `waad.sh`) to LF, and added `.gitattributes` (`*.sh text eol=lf`, `docker-entrypoint.sh text eol=lf`) so Windows checkouts never reintroduce CRLF. Git blob confirmed LF; `git add --renormalize` produced no further changes.

## 2. Engine folder — moved or copied?
Already **copied** (in earlier commit `6348e4a`) to `tools/classification-engine/` — the original Windows folder was **not** deleted. Contains `classify_json.py`, `tpa_service_mapper.py`, `ingest.py`, `medical_synonyms.json`, `odoo_knowledge.json`, approved-category & reference Excel files, `requirements.txt`, and Arabic-named docs — all preserved.

## 3. Final host path
`D:\waad_sofyan_final\tools\classification-engine` (bind-mounted read-only).

## 4. Final container path
`/app/tools/classification-engine` (env `ENGINE_SCRIPT_DIR`, overriding any DB value — no Windows path used at runtime).

## 5. Python runtime installed
Backend image switched to Debian-based `eclipse-temurin:21-jre-jammy`; installed `python3` + `python3-venv` into an isolated venv at `/opt/engine-venv` (`ENGINE_PYTHON_PATH=/opt/engine-venv/bin/python`). Container reports **Python 3.10.12**.

## 6. Python dependencies installed
From `tools/classification-engine/requirements.txt` (inlined in the Dockerfile, manylinux wheels — no native build): **pandas 2.3.3, openpyxl 3.1.5, RapidFuzz 3.14.5, pdfplumber 0.11.10, python-pptx 1.0.2**.

## 7. Engine health result
`GET /api/v1/classification/imports/engine/health` → **status: success / "OK"**. `waad.ps1 doctor` + `health` both report **"classification engine health endpoint READY"**.

## 8. Actual command executed inside container
```
docker exec waad-local-backend /opt/engine-venv/bin/python -X utf8 \
  /app/tools/classification-engine/classify_json.py --help
```
→ printed usage successfully. Files confirmed visible: `/app/tools/classification-engine/classify_json.py` and `/opt/engine-venv/bin/python`.

## 9. Actual classification test result
Uploaded a fresh dental price list via `POST /api/v1/classification/imports` (provider 1, hint=dental) → import **#16**:
- status → **CLASSIFIED**
- **engineVersion = `waad-mce-cli/1.0.0`**, fuzz engine = **rapidfuzz**, totalLines = 102, executionMs ≈ 2021, **no error**
- backend log: `input=/app/uploads/classification/…​.xlsx` — **container path, no Windows path**, no python-missing, no 500.

## 10. `waad.ps1 doctor` result
**All [OK]** — including engine host folder, all engine files, backend container can see `classify_json.py`, backend Python 3.10.12, `classify_json.py --help` executes in container, and engine health **READY**. `Git: main @ 3910e30` (pre-CRLF-fix line; now `c98f463`).

## 11. `waad.ps1 health` result
**All [OK]** — backend reachable, frontend reachable, classification engine health **READY**.

## 12. Was any dev data reset?
**No.** No DB reset, no volume deletion, no destructive commands. The V74 migration was already applied; existing imports/versions were preserved (idempotency correctly rejected re-uploading already-imported files). Legacy `v4/v5` PATCH versions left untouched (v5 is ACTIVE and holds the live pricing rows).

## 13. Does any old Windows path remain in active Docker runtime config?
**No.** Runtime `engine.script.dir` = `/app/tools/classification-engine` via `ENGINE_SCRIPT_DIR`, python via `/opt/engine-venv/bin/python`. The Windows path may still sit in the DB `classification_settings` row but is **overridden** by the env and never used inside the container.

## 14. MC-4C simplified workflow (regression check from committed main)
All row-level edits returned **HTTP 200**, audit grew to 18 rows, and **contract 1 versions stayed 5 → 5 (no new version created):**
- price correction ✅ · add service ✅ · deactivate service ✅ · classification/code edit ✅ · audit trail ✅
- currency renders `د.ل` via the central formatter (unchanged).

## Remaining limitations
- The Windows path persists (harmlessly) in the DB `classification_settings` row; the env override supersedes it. Optional future cleanup: update that row to the container path or make the seed env-driven.
- The bind mount is read-only; engine scripts can be edited on the host and take effect without an image rebuild, but **dependency changes still require an image rebuild**.
- Original Windows engine folder (`للمرافق معالجة اكسيل  سكربت`) retained as a backup; can be removed once you're satisfied.

## Git state
`main` is the sole branch (local + `origin/main`), fully synced (`0 0`), `origin/HEAD → origin/main`. Deleted the two fully-merged remote branches: `codex/tba` and `mce-mc4c-exception-workflow-…`. Working tree clean.

---

## FINAL STATUS

**CLASSIFICATION ENGINE DOCKER READY**

`.\waad.ps1 up` starts the full self-contained stack (frontend + backend + DB connection + classification engine) with no local backend, no Windows venv, and no Windows path at runtime.
