# [Week 1] Lead/DevOps: Dockerize, Testcontainers base and CI hardening
**Branch:** `feature/devops-<topic>`

**Goal:** make the repo reliable for everyone and keep integration smooth.

## Tasks
- [ ] Verify on a clean clone: `docker compose up -d`, `mvn verify`, app boots. Fix any gaps and document them.
- [ ] Add `backend/Dockerfile` (multi-stage, Java 21) and a `backend` service in compose behind a `full` profile.
- [ ] Add a Testcontainers base class (pgvector/pgvector:pg16, declared compatible with postgres) other members reuse for integration tests.
- [ ] Add `OpenApiConfig` (title, version, bearer-auth scheme placeholder) so Swagger UI works at /swagger-ui.html.
- [ ] Review every PR within 24h; keep CI green.

## Done when
A new teammate can clone, follow the README, and have everything running in under 15 minutes.


---
**Reminder:** commit every working day (small, Conventional Commits), open a PR early, merge with *Rebase and merge*. Never commit secrets.
