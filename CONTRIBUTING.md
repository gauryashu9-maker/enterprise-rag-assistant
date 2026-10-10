# Contributing

## The rule that decides if we get evaluated
Evaluation happens **only if every member has real commits across all 4 weeks (20 working days)**. A big dump of code at the end gets the project rejected without review. So:

- Commit at least **once every working day**, small and meaningful.
- Never pile up work locally and push it all at once.
- Set `git config user.email` to the email on your GitHub account, or your commits will not count as yours.

## Workflow
1. Pick an issue, create a branch from `main`: `feature/<area>-<short-name>` (e.g. `feature/ingestion-pdf-reader`).
2. Commit small and often.
3. Push daily and open a PR early (draft is fine). Keep PRs small.
4. At least 1 teammate reviews. Fix comments, then merge with **Rebase and merge** (never squash: squash erases everyone's individual commits).
5. Delete the branch, pull `main`, repeat.

## Conventional Commits
Format: `type(scope): short description` in the imperative.

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

```
feat(ingestion): split PDF pages into token chunks
fix(rag): return fallback answer when no chunks match
test(security): cover expired JWT rejection
docs: document env variables in README
```
CI rejects PRs with commits that do not follow this.

## Code rules (from the PRD)
- Layers: `config`, `controllers`, `services`, `repositories`, `entities`, `dto`, `exceptions`.
- Controllers never return JPA entities. Map to DTO **Records**.
- No business logic in controllers.
- Errors via the global `@RestControllerAdvice` (ProblemDetail).
- Write tests for your own module. `mvn verify` must pass before you push.

## Secrets
Never commit API keys, passwords or `.env`. Use `.env` locally (git-ignored) and `.env.example` for the template. If you leak a key, tell the lead immediately and rotate it.
