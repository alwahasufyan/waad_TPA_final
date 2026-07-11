# 02 — Scorecard

> Scores are 0–100, scored independently per area against ATEF's own review checklists (`.claude/reviews/*`, `.claude/Constitution/*`). Scores are **not weighted equally in importance** — see the two composite views at the bottom. Full justification for every score is in the corresponding numbered review document.

| # | Area | Score /100 | Grade | Trend Signal |
|---|---|---|---|---|
| 1 | Architecture | **68** | C+ | Sound module boundaries with one serious bounded-context violation |
| 2 | Security | **58** | D+ | Good defense-in-depth patterns undermined by one critical gap |
| 3 | Financial Integrity | **82** | B+ | The system's clear strength |
| 4 | Medical Integrity | **70** | B− | Sound governance, clouded by dual coverage engines |
| 5 | Database | **65** | C | Real constraint discipline, real historical churn scars |
| 6 | Backend | **70** | B− | Consistent structure, several duplicate-implementation smells |
| 7 | Frontend | **60** | C− | Functional but several monolithic files and dead scaffold code |
| 8 | User Experience | **66** | C+ | Genuinely operator-aware in places, inconsistent elsewhere |
| 9 | Provider Portal | **55** | D+ | Functional but least mature UI surface, no mobile support |
| 10 | Reporting | **68** | C+ | Solid business coverage, weak error/failure handling |
| 11 | Printing | **60** | C− | Correct fundamentals (A4/RTL/fonts), unconfirmed enterprise extras |
| 12 | PDF | **62** | C | Right technical choice, silent-failure risk |
| 13 | Localization | **78** | B | The system's second-clearest strength |
| 14 | Notification System | **15** | F | Does not exist beyond a mock UI widget |
| 15 | Medical Service Classification | **30** | F+ | Manual, developer-dependent, not scalable |
| 16 | Mobile Readiness | **25** | F+ | Desktop-only in the highest-traffic workflows |
| 17 | Performance | **65** | C | Evidence of real tuning where it matters, gaps elsewhere |
| 18 | Technical Debt Management | **55** | D+ | Large, well-understood register; not yet being actively burned down |
| 19 | Production Readiness | **64** | C | Operational today; would not pass a rigorous audit unchanged |

---

## Composite Views

### Core Viability Score (Architecture, Security, Financial, Medical, Database, Backend)
```
(68 + 58 + 82 + 70 + 65 + 70) / 6 = 68.8 → 69/100
```
**This is the number that matters most for "should this system be replaced?"** — and the answer is unambiguously no. A 69 on the areas that determine whether a TPA's core adjudication logic can be trusted is a passing, improvable score, not a failing one. Every point below 100 here has a named, addressable cause in this assessment's findings — none of them require starting over.

### Full-Platform Composite Score (all 19 areas, unweighted)
```
Sum = 1,116 → 1,116 / 19 = 58.7 → 59/100
```
**This lower number is dragged down almost entirely by three areas that were never built, not built badly**: Notification System (15), Mobile Readiness (25), Medical Classification (30). Removing just these three from the average raises the remaining 16-area composite to **66/100**. This distinction matters for prioritization: the system is not "59/100 broken" — it is "69/100 solid core, with three greenfield capabilities the business has not yet invested in building."

---

## How to Read These Scores

- **A score in the 70s–80s** means the area reflects real engineering discipline with a short, addressable list of gaps (Financial Integrity, Localization).
- **A score in the 55–70 range** means the area is functionally sound in production today but carries meaningful, named structural debt that will compound if ignored (Architecture, Backend, Database, Medical Integrity, Reporting, Performance, UX).
- **A score in the 55–65 range with a "least mature" label** (Frontend, Provider Portal, Printing, PDF, Production Readiness, Technical Debt Management) means the fundamentals work, but the area has not received the same sustained investment as the financial/domain core.
- **A score below 35** (Notification, Mobile, Medical Classification) means the capability is either absent or exists only as an ad-hoc/manual workaround — these are roadmap items, not bug-fix items, and are treated that way in `21-enterprise-roadmap.md` (Epics 6–8).

## Scoring Discipline

Per the Engineering Constitution's Decision Hierarchy, scores were assigned based on: (1) Business/financial/medical correctness first, (2) architectural and security soundness second, (3) operational/UX polish third. This is why **Financial Integrity (82) outscores Frontend (60)** even though both have real, documented issues — the financial core's issues are narrow and contained (one magic number, one unverified FK), while the frontend's issues are broader (monolithic files, dead template code, unresolved component duplication) even though neither area is in crisis.

---

*Continue to [`03-critical-findings.md`](./03-critical-findings.md) for the findings that require action before any other roadmap work.*
