# Live Presentation Script (HM Review)

**Note:** For this presentation, we run the backend and frontend locally (not via Docker) to show live logs and the streaming RAG pipeline in real-time.

## Pre-Demo Setup
1. Start Database: `docker compose up -d postgres`
2. Start Backend: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8080`
3. Start Frontend: `cd frontend && npm run dev`
4. Verify User: Ensure `test@example.com` exists in the database. If not, register via Swagger or Curl.

## The Demo Flow
1. **The Fallback:** Log in and ask "How many days of paid leave do employees get?" -> *Point out that it says "I don't have that information" proving it does not hallucinate.*
2. **Upload & ETL:** Upload `smoke.txt`. -> *Explain that it is extracting text, chunking, and vectorizing via pgvector.*
3. **The Stream:** Ask the same question. -> *Point out the word-by-word SSE streaming and the exact citation from `smoke.txt`.*
4. **Delete:** Delete the document in the UI.
5. **The Proof:** Ask the question again. -> *Point out the fallback, proving the vectors were successfully wiped from the database.*

## Emergency Fallbacks
- **UI Kicks you out (401):** Token expired. Mint a new one via `mint_token.py` and inject into localStorage.
- **UI Upload fails:** Run the `curl` upload command in the terminal.