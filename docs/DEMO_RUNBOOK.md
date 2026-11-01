# DevOps & Full-Stack Demo Runbook

This runbook outlines the verification and demonstration flow for the Enterprise RAG Document Assistant in a local Docker environment.

## 1. Prerequisites
- Docker Desktop is installed and running (Engine active).
- Local environment configuration `.env` is present matching `.env.example`.

## 2. Launch Services
To build and spin up PostgreSQL, Spring Boot backend, and React frontend simultaneously:
```bash
docker compose up -d --build