# Knowledge Desk frontend

React UI for document upload, library selection, questions, citations, evidence inspection and JSON export.

From the repository root:

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

Open http://127.0.0.1:5174. Vite proxies `/api` to Spring Boot at 8081. The backend needs PostgreSQL; uploads/search also need the embedding model. Generated answers need the chat model.

`npm run build --prefix frontend` creates `frontend/dist`. The root packaging script copies this into the JAR.

Browser tests use a disposable PostgreSQL test database and a **test-only** Ollama protocol stub. They do not evaluate model quality. See [evaluation instructions](../docs/EVALUATION.md).

The UI renders document/model text as plain React text. It does not interpret uploaded HTML or model Markdown as executable HTML. Questions and answers are not stored in localStorage.
