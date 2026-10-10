# [Week 1] Frontend: React scaffold, login and API client
**Branch:** `feature/frontend-scaffold`

**Goal:** a running React shell the rest of the app plugs into.

## Tasks
- [ ] Scaffold Vite + React in `frontend/` (commit `package-lock.json`).
- [ ] Routing: /login, /documents, /chat (placeholder pages).
- [ ] Axios instance: baseURL from `VITE_API_URL`, request interceptor adding the JWT, 401 handling that sends the user to /login.
- [ ] Login page wired to `POST /api/auth/login` (use mock data until the Security endpoint lands).
- [ ] Vite dev proxy for `/api` to `http://localhost:8080`. Add `frontend/.env.example`.
- [ ] `npm run build` passes (CI runs it).

## Done when
You can log in (mock or real), land on /chat, and the token is sent on API calls.


---
**Reminder:** commit every working day (small, Conventional Commits), open a PR early, merge with *Rebase and merge*. Never commit secrets.
