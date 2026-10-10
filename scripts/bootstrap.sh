#!/usr/bin/env bash
# Creates the git history (small, conventional commits), the GitHub repo,
# invites the team, and opens the Week 1 issues. Run ONCE from Git Bash / WSL / macOS / Linux.
#   1) fill scripts/team.conf   2) gh auth login   3) bash scripts/bootstrap.sh
set -euo pipefail
cd "$(dirname "$0")/.."

warn() { echo "WARN: $*" >&2; }
die()  { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "Missing tool: $1"; }

need git; need gh
gh auth status >/dev/null 2>&1 || die "Not logged in. Run: gh auth login"
# shellcheck disable=SC1091
source scripts/team.conf
[ -n "${REPO_OWNER:-}" ] || die "Fill REPO_OWNER in scripts/team.conf"
[ -d .git ] && die "This folder is already a git repo. Nothing to do."
git config user.name  >/dev/null || die "Set: git config --global user.name  \"Your Name\""
git config user.email >/dev/null || die "Set: git config --global user.email \"email-used-on-github\""

FULL="$REPO_OWNER/$REPO_NAME"
VIS="${VISIBILITY:-private}"

# --- 1. Prove the skeleton builds BEFORE anything is pushed ---------------------
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  need mvn
  echo ">> Building backend (mvn verify)..."
  mvn -B -q -f backend/pom.xml verify || die "Build failed. Fix it (or send me the error) before pushing. SKIP_BUILD=1 to bypass."
fi

# --- 2. History: small, logical, conventional commits ---------------------------
git init -q -b main
c() { local msg="$1"; shift; git add -- "$@"; git commit -q -m "$msg"; echo "   commit: $msg"; }

c "chore: initialize repository with gitignore and gitattributes" .gitignore .gitattributes
c "docs: add project overview, team roles and contribution guide" README.md CONTRIBUTING.md
c "ci: add Maven build, frontend build and conventional-commit check" .github/workflows
c "docs: add pull request template" .github/pull_request_template.md
c "chore(infra): add pgvector docker-compose and env template" docker-compose.yml docker .env.example
c "build: add Spring Boot 3.5 Maven project with Spring AI, JPA, Security, springdoc and JWT" backend/pom.xml
c "feat: add Spring Boot application entry point" backend/src/main/java/com/axlero/ragassistant/RagAssistantApplication.java
c "chore: scaffold layered architecture packages" \
  backend/src/main/java/com/axlero/ragassistant/config/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/controllers/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/services/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/repositories/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/entities/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/dto/package-info.java \
  backend/src/main/java/com/axlero/ragassistant/exceptions/package-info.java
c "feat(config): add datasource, Spring AI and pgvector configuration" backend/src/main/resources/application.yml
c "feat(security): add temporary permit-all security config until JWT lands" backend/src/main/java/com/axlero/ragassistant/config/SecurityConfig.java
c "feat(ai): add ChatClient ping endpoint to verify LLM connectivity" \
  backend/src/main/java/com/axlero/ragassistant/config/AiConfig.java \
  backend/src/main/java/com/axlero/ragassistant/dto/PingResponse.java \
  backend/src/main/java/com/axlero/ragassistant/services/AiPingService.java \
  backend/src/main/java/com/axlero/ragassistant/controllers/AiPingController.java
c "test: add unit test for AiPingService" backend/src/test/java/com/axlero/ragassistant/services/AiPingServiceTest.java
c "docs(frontend): add frontend workspace placeholder" frontend
c "docs: add week 1 task briefs for each role" docs
c "chore: add team setup scripts" scripts

# --- 3. GitHub repo + push --------------------------------------------------------
echo ">> Creating GitHub repo $FULL ($VIS)..."
gh repo create "$FULL" "--$VIS" --source=. --remote=origin --push \
  --description "Axlero internship Project 2: Enterprise RAG Document Assistant (Spring Boot + Spring AI + pgvector + React)"

# No squash merges: they would erase individual contributors' commits.
gh repo edit "$FULL" --enable-squash-merge=false --enable-rebase-merge=true \
  --enable-merge-commit=true --delete-branch-on-merge || warn "Could not change merge settings; set them in repo Settings."

# Require a PR + 1 approval on main (needs a public repo or a paid plan on private repos).
gh api -X PUT "repos/$FULL/branches/main/protection" --input - >/dev/null <<'JSON' \
  || warn "Branch protection not applied (private repos on the free plan do not support it). Agree as a team: nobody pushes to main directly."
{"required_status_checks":null,"enforce_admins":false,"required_pull_request_reviews":{"required_approving_review_count":1},"restrictions":null}
JSON

# --- 4. Invite the team ----------------------------------------------------------
for u in "${GH_LEAD:-}" "${GH_SECURITY:-}" "${GH_INGESTION:-}" "${GH_RAG:-}" "${GH_DATA:-}" "${GH_FRONTEND:-}"; do
  [ -z "$u" ] && continue
  [ "$u" = "$REPO_OWNER" ] && continue
  gh api -X PUT "repos/$FULL/collaborators/$u" -f permission=push --silent \
    && echo "   invited: $u" || warn "Could not invite $u"
done

# --- 5. Labels, milestones, Week 1 issues -----------------------------------------
for w in 1 2 3 4; do
  gh label create "week-$w" --repo "$FULL" --color "0E8A16" >/dev/null 2>&1 || true
  gh api "repos/$FULL/milestones" -f title="Week $w" --silent >/dev/null 2>&1 || true
done

assignee_for() {
  case "$1" in
    lead) echo "${GH_LEAD:-}" ;;       security) echo "${GH_SECURITY:-}" ;;
    ingestion) echo "${GH_INGESTION:-}" ;; rag) echo "${GH_RAG:-}" ;;
    data) echo "${GH_DATA:-}" ;;       frontend) echo "${GH_FRONTEND:-}" ;;
  esac
}

for f in docs/week1-issues/*.md; do
  key="$(basename "$f" .md | cut -d- -f2-)"
  title="$(head -n1 "$f" | sed 's/^# //')"
  body="$(mktemp)"; tail -n +2 "$f" > "$body"
  who="$(assignee_for "$key")"
  if [ -n "$who" ] && gh issue create --repo "$FULL" --title "$title" --body-file "$body" \
        --label week-1 --milestone "Week 1" --assignee "$who" >/dev/null 2>&1; then
    echo "   issue (assigned to $who): $title"
  else
    gh issue create --repo "$FULL" --title "$title" --body-file "$body" \
      --label week-1 --milestone "Week 1" >/dev/null
    echo "   issue (unassigned - assign after they accept the invite): $title"
  fi
  rm -f "$body"
done

echo
echo "Done: https://github.com/$FULL"
echo "Next: teammates accept the email invites, clone, copy .env.example to .env, and start committing."
