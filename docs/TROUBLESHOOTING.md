# Troubleshooting

## npm cannot find package.json or the lockfile

Run commands from the project root, not your home directory. `ls` should show `pom.xml`, `compose.yaml` and `frontend`. Then use `npm ci --prefix frontend` and `npm run dev --prefix frontend`.

## Java version errors

Use `java -version` and `./mvnw -version`; both must show Java 21. Also set IntelliJ's project SDK and Maven runner JDK to 21. Restart terminals after changing your Java path.

## Backend fails to start / database connection refused

Open Docker, run `docker compose up -d`, then `docker compose ps`. Verify port 5433 is available. The JDBC database name and password must match the Compose environment. Changing a password in Compose does not change an existing volume's database password automatically.

## vector extension is unavailable

Use the `pgvector/pgvector:pg17` image in the included Compose file, not plain PostgreSQL. An externally managed database needs the vector extension installed and a user able to initialize the schema.

## Backend connected but upload/answer fails

Backend status is not model readiness. Open Ollama and run `ollama list`. Both `nomic-embed-text` and `llama3.2` should be downloaded. Test embedding directly:

```bash
curl --max-time 60 http://127.0.0.1:11434/api/embed \
  -H 'Content-Type: application/json' \
  -d '{"model":"nomic-embed-text","input":"search_query: incident updates"}'
```

## Processing times out / 429 busy

A first model load may be slow. Try a short document, then Sources only. Wait before retrying; the worker can still be finishing after the caller times out. Refresh the library before repeating an upload. If Ollama itself hangs, restart it. Increasing a deadline does not fix a stalled model.

## No evidence for an answer you expected

Select All documents, verify upload succeeded, and try a more specific question. Use Sources only to inspect retrieval. PDF extraction can miss text in images, complex tables or unusual layouts. The threshold is a starting value; tune it with labelled questions. Lowering it may introduce irrelevant passages.

## Invalid citations / model response

The model may fail the requested JSON or citation format. Retry once with a shorter question, or use Sources only. The backend rejects an invalid answer instead of inventing citations. A valid citation is still not proof that the claim is supported.

## Embedding model changed / dimension error

Keep `nomic-embed-text` for this version. The schema expects 768 dimensions. To change the model deliberately, re-index the corpus with a compatible schema and prompt strategy; do not mix old and new embeddings.

## Root page returns 404

During development open the Vite UI at 5174. The backend serves the UI at 8081 only after running the packaging script.

## Stop the project

Press Ctrl+C in the frontend/backend terminals and run `docker compose down` from the project root. This keeps indexed documents. Deleting Docker volumes deletes that persistent data; do that only when you intentionally want to erase the workspace.
