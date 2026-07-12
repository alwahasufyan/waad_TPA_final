# Cleanup Quarantine Manifest — 20260712

Scope: Project Cleanup Phase 2 — Limited Quarantine Only.

No files were deleted. Files listed here were moved from the project root into this quarantine folder.

| Original path | Quarantine path | Reason | Risk level | Timestamp |
|---|---|---|---|---|
| `D:\waad_sofyan_final\hex_dump.txt` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\hex_dump.txt` | Temporary hex/debug dump from prior investigation. | Low | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\hex2.txt` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\hex2.txt` | Temporary hex/debug dump from prior investigation. | Low | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\last_info.txt` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\last_info.txt` | Old captured runtime/info output. | Low | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\check_db.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\check_db.py` | One-off local database inspection script; no references found in backend, frontend, Docker scripts, deployment scripts, classification engine, or docs. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\restart_backend.bat` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\restart_backend.bat` | Old manual backend restart helper superseded by Docker workflow; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\read_startup_errors.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\read_startup_errors.py` | One-off startup error log reader; no references found. | Low | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\tmp_analyze_rules.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\tmp_analyze_rules.py` | Temporary DB/rules analysis script; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\tmp_analyze2.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\tmp_analyze2.py` | Temporary DB/rules analysis script; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\tmp_inspect_safwa.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\tmp_inspect_safwa.py` | Temporary provider-specific Excel inspection script; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\tmp_we007.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\tmp_we007.py` | Temporary WE-007 pricing/rules diagnostic script; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |
| `D:\waad_sofyan_final\tmp_we007b.py` | `D:\waad_sofyan_final\_cleanup_quarantine\20260712\tmp_we007b.py` | Temporary WE-007 pricing/rules diagnostic script; no references found. | Medium | `2026-07-12T01:02:59.4405987+02:00` |

Intentionally not moved:

- `D:\waad_sofyan_final\tmp_pricingcheck.py` — referenced by `docs/assessment/17-medical-classification-review.md`.
- All protected folders/files named in the cleanup request, including classification engine folder, `draft/`, root Excel files, `logs/`, `uploads/`, env files, SSL files, Docker workflow files, docs, deployment, and source modules.
