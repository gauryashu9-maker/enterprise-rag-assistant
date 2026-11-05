#!/usr/bin/env bash
# Demo preflight: prints PASS or FAIL for everything the live demo depends on. Run from the repo root.
FAILED=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; FAILED=1; }
val() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '\r'; }

docker ps >/dev/null 2>&1 && pass "Docker is running" || fail "Docker is not running"
docker exec rag-postgres pg_isready -U rag >/dev/null 2>&1 && pass "Postgres is ready" || fail "Postgres container is not ready"

TAGS=$(curl -sf http://localhost:11434/api/tags 2>/dev/null)
[ -n "$TAGS" ] && pass "Ollama answers" || fail "Ollama is not running"
EMB=$(val AI_EMBEDDING_MODEL); CHAT=$(val AI_CHAT_MODEL); DIM=$(val EMBEDDING_DIMENSIONS)
echo "$TAGS" | grep -q "$EMB" && pass "Embedding model installed: $EMB" || fail "Embedding model missing: $EMB"
echo "$TAGS" | grep -q "$CHAT" && pass "Chat model installed: $CHAT" || fail "Chat model missing: $CHAT"
LOADED=$(ollama ps 2>/dev/null)
if echo "$LOADED" | grep -q "$EMB" && echo "$LOADED" | grep -q "$CHAT"; then pass "Both models are warm"; else fail "Models are cold: send one warm-up request to each"; fi

[ -n "$(val JWT_SECRET)" ] && pass "JWT_SECRET is set" || fail "JWT_SECRET is missing in .env"
[ -n "$(val OPENAI_API_KEY)" ] && pass "OPENAI_API_KEY is set (any word for Ollama)" || fail "OPENAI_API_KEY is empty"

DBN=$(val DB_NAME); DBN=${DBN:-ragdb}
DBDIM=$(docker exec rag-postgres psql -U rag -d "$DBN" -tAc "select atttypmod from pg_attribute where attrelid='vector_store'::regclass and attname='embedding'" 2>/dev/null | tr -d '\r ')
if [ -n "$DBDIM" ] && [ "$DBDIM" = "$DIM" ]; then pass "Vector table size $DBDIM matches EMBEDDING_DIMENSIONS"; else fail "Vector table size '$DBDIM' differs from EMBEDDING_DIMENSIONS '$DIM' (a fresh database creates the table when the backend starts)"; fi

curl -sf http://localhost:8080/actuator/health | grep -q UP && pass "Backend is up" || fail "Backend is not running"
[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/swagger-ui/index.html)" = "200" ] && pass "Swagger UI answers" || fail "Swagger UI does not answer"
[ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/)" = "200" ] && pass "Frontend is up" || fail "Frontend (npm run dev) is not running"

BR=$(git rev-parse --abbrev-ref HEAD)
[ "$BR" = "main" ] && pass "On main" || fail "Not on main (on $BR)"
[ -z "$(git status --short)" ] && pass "Working tree is clean" || fail "Uncommitted changes"
git fetch origin >/dev/null 2>&1
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] && pass "Same as origin/main" || fail "Differs from origin/main"
CI=$(gh run list --branch main --limit 1 --json conclusion --jq '.[0].conclusion' 2>/dev/null)
[ "$CI" = "success" ] && pass "Latest CI run on main is green" || fail "Latest CI run on main: ${CI:-unknown}"

echo
if [ "$FAILED" = "0" ]; then echo "ALL GOOD"; else echo "FIX THE FAIL LINES BEFORE THE DEMO"; fi
exit $FAILED
