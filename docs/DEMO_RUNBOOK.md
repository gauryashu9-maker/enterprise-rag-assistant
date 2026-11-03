# DevOps & Full-Stack Demo Runbook

This runbook outlines the verification and demonstration flow for the Enterprise RAG Document Assistant in a local Docker environment.

## 1. Prerequisites
- Docker Desktop is installed and running (Engine active).
- Local environment configuration `.env` is present matching `.env.example`.

## 2. Launch Services
To build and spin up PostgreSQL, Spring Boot backend, and React frontend simultaneously:
```bash
docker compose up -d --build
```

## 3. Health & Status Checks
Verify all container services are running:
```bash
docker compose ps
```

Expected status:
- `rag-postgres`: Healthy on port 5432
- `rag-backend`: Up on port 8080
- `rag-frontend`: Up on port 80

## 4. Verification Endpoints
- **Frontend UI**: Open `http://localhost:80` to verify the React client and authentication landing page.
- **OpenAPI / Swagger Documentation**: Open `http://localhost:8080/swagger-ui/index.html` to inspect active REST endpoints.
- **Backend Health Check**: Access `http://localhost:8080/actuator/health` to confirm Spring Boot Actuator status (public endpoint).
- **Spring Security Verification**: Access `http://localhost:8080/api/ai/ping` — returns `401 Unauthorized` without a JWT bearer token, confirming active security filters.
## 5. Teardown
To cleanly stop containers and free system resources:
```bash
docker compose down
```
---

## 6. Docker Troubleshooting & Fallback Recovery

If any container encounters issues during runtime or demonstration, follow these quick recovery steps:

### Scenario A: Port Conflicts (Port 8080 or 5432 in use)
Identify and terminate the occupying background PID:
```bash
netstat -ano | findstr :8080
netstat -ano | findstr :5432
taskkill /PID <PID> /F

docker compose logs rag-postgres
docker compose restart rag-backend
docker compose down -v
docker compose up -d --build