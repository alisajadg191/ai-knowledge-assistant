# Testing and evaluation

## Verified in the build workspace — 5 October 2026

| Check | Result |
|---|---|
| Java 21 Maven clean verify and executable JAR packaging | Passed |
| Unit and Spring AI HTTP protocol tests | 19 passed, zero failures |
| Real PostgreSQL/pgvector integration tests | 5 implemented; skipped locally because this workspace has no usable PostgreSQL/Docker runtime |
| React production build | Passed |
| Desktop/mobile browser tests | 4 implemented; require the disposable database and browser runtime; not yet verified |
| Live Ollama embedding/retrieval/answer quality | Not evaluated here |
| GitHub Actions | Workflow supplied; publication and first CI run pending repository creation |

Do not read implemented tests as passing tests. Update this document with actual CI results once the repository is published.

The Java protocol tests exercise actual Spring AI serialization and HTTP clients against a controlled Ollama-compatible stub. The stub returns fixed vectors/answers and is **not inference**. Unit tests cover PDF page extraction, empty/scanned/unsupported text, chunk limits, duplicate handling, ingestion failure, retrieval branches, citation validation, model admission and timeouts.

## Run ordinary tests

```bash
./mvnw test
npm ci --prefix frontend
npm run build --prefix frontend
```

No running model or database is needed for the ordinary Java suite. Five database tests are skipped by default.

## Run database and browser tests safely

Use a dedicated database ending in `_test`. The integration suite and browser suite delete their document data. Do not point them at the normal `knowledge` workspace.

With the project's database container running, create a separate test database:

```bash
docker compose exec postgres createdb -U knowledge knowledge_test
```

If it already exists, reuse it only if it contains disposable test data. Stop any normal app on ports 8081 and 5174 before browser testing.

```bash
export DATABASE_URL=jdbc:postgresql://127.0.0.1:5433/knowledge_test
export DATABASE_USER=knowledge
export DATABASE_PASSWORD=knowledge_local
export RUN_PGVECTOR_TESTS=true
bash scripts/package.sh
cd frontend
npx playwright install chromium
npm run test:e2e
```

Use a fresh terminal afterwards so test environment variables do not carry into normal development. If you customized the database password, use that value above.

The database tests cover real pgvector similarity, document filtering, threshold behavior, atomic rollback, duplicate uploads, model mismatch, cascading deletion and HTTP validation. They mock the AI client to isolate database behavior.

Browser tests start a test-only Ollama protocol stub on 11435, the packaged backend on 8081 and Vite on 5174. They cover upload, cited answer/source navigation, source-only mode, export, deletion, no-evidence output, visible model failure and mobile overflow. They use real application/database paths with fixed model responses. They do not prove live model quality.

The GitHub Actions workflow provisions an isolated pgvector service and runs this full sequence.

## Evaluate real RAG quality on your computer

Use the two example documents, the real downloaded models and a separate normal workspace. Repeat each question three times. Record the retrieved filenames/passages, answer, citations, duration and any error.

| Question | Expected evidence/behavior |
|---|---|
| How often should incident updates be posted? | Incident runbook; 30 minutes |
| Who approves a rollback? | Incident runbook; incident commander, with a documented recovery plan |
| When should the follow-up review be created? | Incident runbook; within two working days |
| How many code review approvals are needed? | Handbook; one independent engineer, plus a security-owner review for authentication/access-control changes |
| When are deployments normally scheduled? | Handbook; 09:00–16:00 on working days |
| What is Sajad's salary? | Unsupported; should decline |

Assess retrieval separately from generation. If the relevant passage was not retrieved, inspect chunking/threshold/filtering. If it was retrieved but the answer is wrong, inspect the prompt/model behavior. A syntactically valid citation is not an entailment check.

No accuracy percentage is claimed without a recorded live evaluation. The threshold of 0.45 is an initial configuration, not a validated universal cutoff.
