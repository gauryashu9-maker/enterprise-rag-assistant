# [Week 1] Security: JWT authentication
**Branch:** `feature/security-jwt`

**Goal:** replace the temporary permit-all `SecurityConfig` with stateless JWT auth.

## Tasks
- [ ] `User` entity (id, email unique, passwordHash, role) + `Role` enum (ADMIN, USER) + `UserRepository`.
- [ ] `JwtService` (jjwt 0.12: generate, validate, extract username). Secret comes from the `JWT_SECRET` env var, never committed.
- [ ] `JwtAuthenticationFilter` (OncePerRequestFilter) and a stateless `SecurityFilterChain`.
- [ ] `AuthController`: `POST /api/auth/register`, `POST /api/auth/login` using Record DTOs with validation.
- [ ] BCrypt `PasswordEncoder` bean.
- [ ] Public paths: `/api/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/health`. Everything else needs a valid token.
- [ ] Add `CorsConfig` for the React dev server.
- [ ] Unit/slice tests: valid token, expired token, wrong password.

## Done when
Register, login, and calling a protected endpoint with the Bearer token all work, and tests pass.


---
**Reminder:** commit every working day (small, Conventional Commits), open a PR early, merge with *Rebase and merge*. Never commit secrets.
