# AI Knowledge Assistant · Knowledge Desk

A local document Q&A portfolio application by **Sajad Ali**, using **Java 21, Spring Boot, Spring AI, React, Ollama and PostgreSQL with pgvector**.

Upload documents, retrieve relevant passages with embeddings, and ask a local model to answer using those passages. Inspect clickable citations, page numbers for PDFs, and the original excerpts behind an answer.

**Start with the [project requirements and walkthrough](docs/PROJECT_REQUIREMENTS.md)** if RAG, embeddings or vector databases are new to you.

## Features

- Upload UTF-8 TXT/Markdown and text-based PDF documents.
- Split extracted text into overlapping passages and create local Ollama embeddings.
- Persist document metadata, extracted passages and 768-dimensional vectors in PostgreSQL.
- Search the whole library or one selected document using exact cosine similarity in pgvector.
- Generate structured answers with source references, or inspect retrieval with **Sources only**.
- Decline when retrieval finds no sufficiently similar passages, or when the model says the context is insufficient.
- Validate citation numbers and their correspondence with inline references.
- Show source excerpts, filenames, passage numbers, PDF pages and similarity scores.
- Detect identical uploads by SHA-256; delete a document and its passages together.
- Export the current answer and sources as JSON.
- Bound AI waiting, reject concurrent model operations, and show actionable errors.

This is a **single-user local portfolio application**, developed with AI assistance. Citation validation is not factual verification. A model can still misinterpret a passage or make an unsupported claim with a valid source number. Always inspect the cited text.

## Requirements

- JDK **21** (also select JDK 21 in IntelliJ's Project SDK and Maven runner).
- Node.js **22.12+** and npm.
- Docker with Docker Compose for PostgreSQL/pgvector, or an equivalent existing pgvector installation.
- [Ollama](https://ollama.com/) running locally.
- Git. Maven is provided through the wrapper.

Unlike Incident Desk's template demo, this project needs an embedding model to upload or search documents. No paid API key is required. The first dependency and model downloads require internet access.

## Run step by step

Run commands **inside this project's root folder**, where `pom.xml`, `compose.yaml` and `frontend/` are visible.

### 1. Get the project

Clone the repository, then open `ai-knowledge-assistant` in IntelliJ:

```bash
git clone https://github.com/alisajadg191/ai-knowledge-assistant.git
cd ai-knowledge-assistant
```

Check the installed versions:

```bash
java -version
./mvnw -version
node --version
```

Both Java checks must show version 21. On Windows use `mvnw.cmd` for direct Maven commands.

### 2. Start the vector database

Open Docker, then run:

```bash
docker compose up -d
```

The database listens at `127.0.0.1:5433`. Its Docker volume persists documents across restarts. `docker compose down` stops it without removing that volume.

### 3. Download the two local models

Open Ollama, then run:

```bash
ollama pull nomic-embed-text
ollama pull llama3.2
```

`nomic-embed-text` turns passages/questions into vectors. `llama3.2` writes answers. These are separate jobs and models. They run through the same Ollama service at port 11434.

### 4. Start the backend

In the first project terminal:

```bash
./mvnw spring-boot:run
```

Wait for the application to start on **8081**. The database must be running; schema initialization creates the tables and vector extension if needed.

### 5. Start the frontend

In a second terminal, still at the project root:

```bash
npm ci --prefix frontend
npm run dev --prefix frontend
```

Open **http://127.0.0.1:5174**. These ports differ from Incident Desk, so both projects can run side by side.

### 6. Try the included example

1. Upload `examples/incident-runbook.md`.
2. Ask: **How often should incident updates be posted?**
3. Expect the answer to mention **30 minutes**, with a source citation. Wording depends on your model.
4. Click the citation and inspect its passage.
5. Select **Sources only** and ask again to inspect retrieval without generating an answer.
6. Upload `examples/engineering-handbook.md`; select it in the sidebar and ask about code review approvals.
7. Ask a question absent from the documents, such as an employee's salary. The intended behavior is to decline. Check live model behavior rather than assuming a guarantee.

Backend connected means the Java API and database responded. It does not prove Ollama or either model is ready.

## Build one executable application

```bash
bash scripts/package.sh
java -jar target/ai-knowledge-assistant-1.0.0.jar
```

Open **http://127.0.0.1:8081**. PostgreSQL and Ollama must still be running. The script embeds React into the JAR; Node is needed for building, not running that JAR. During separate development the UI is on 5174 and the backend root may return 404.

## Technology choices

| Area | Technology and purpose |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, REST endpoints |
| AI | Spring AI 2.0.1 `EmbeddingModel` and `ChatClient`, local Ollama |
| Retrieval | PostgreSQL 17 + pgvector, exact cosine search, top 5 passages |
| Persistence | Spring JDBC, transactional document/chunk writes, cascading deletion |
| Extraction | PDFBox 3.0.5 for PDF pages; strict UTF-8 for text |
| Frontend | React, Vite, responsive CSS |
| Testing | JUnit, Mockito, Spring AI HTTP protocol tests, optional real pgvector integration, Playwright |
| Delivery | Maven wrapper, npm lockfile, Docker Compose, GitHub Actions workflow |

This project uses Spring AI for embeddings and generation, and parameterized JDBC SQL for vector persistence/search. It does **not** use Spring AI's `PgVectorStore` abstraction. Keeping SQL explicit makes document ownership, transactions, filtering and cascading deletion easier to inspect in this small learning project.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5433/knowledge` | JDBC connection |
| `DATABASE_USER` | `knowledge` | Database user |
| `DATABASE_PASSWORD` | `knowledge_local` | Local development password |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Model service |
| `OLLAMA_MODEL` | `llama3.2` | Generation model |
| `EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model; schema requires 768 dimensions |
| `AI_DEADLINE_SECONDS` | `120` | Caller deadline for upload/search/generation |
| `SIMILARITY_THRESHOLD` | `0.45` | Minimum cosine similarity; a starting value, not calibrated confidence |
| `PORT` | `8081` | Backend port; also update the Vite proxy if changed |
| `SERVER_ADDRESS` | `127.0.0.1` | Backend bind address |

Set environment variables in the shell or IntelliJ run configuration. Spring Boot does not automatically load a `.env` file here. Compose may read `.env`, so keep the database password consistent between Compose and Spring Boot. Do not commit credentials.

Changing an embedding model requires deleting/re-indexing existing documents. The app rejects mixed model names. This is a name-based guard: replacing a model behind the same tag cannot be detected. Keep the model fixed for a corpus. The default prompt prefixes are `search_document:` and `search_query:` for the chosen embedding workflow.

## Limits and data handling

- At most 50 documents per local workspace; upload limit 5 MB per file.
- At most 60 PDF pages, 60,000 extracted characters and 100 passages per document.
- Passages use up to 1,200 Java characters with roughly 200-character overlap, within each PDF page. This is character-based, not token-based chunking.
- Scanned/image-only PDFs need OCR elsewhere. Encrypted PDFs, DOCX, web crawling and image understanding are not supported.
- The top 5 qualifying passages form the generation context. Default model budget is 700 output tokens and 8,192 context tokens; unusual token-heavy text can still exceed model capacity.
- One AI operation is admitted at a time. Other operations using that slot receive 429. The default caller deadline is 120 seconds and HTTP read timeout is 60 seconds per model request.
- An upload is stored in one transaction after embeddings succeed. Deadline checks precede and end the transaction. A timeout near commit can still be ambiguous: refresh before retrying. Duplicate detection prevents identical successful uploads from creating another document.
- Original upload binaries are not retained. Extracted passages, filename, hash, model name and vectors persist in PostgreSQL. Questions and answers are held in browser memory, not a server chat history. Downloaded JSON is a separate local copy.
- Documents are sent to the configured Ollama endpoint for embedding; retrieved text and questions are sent for generation. Keep it local for the default local workflow.
- No authentication, tenant isolation, conversation memory, automatic remediation or public hosting is included. Do not expose this local server as a multi-user production service.

## Tests

```bash
./mvnw test
npm ci --prefix frontend
npm run build --prefix frontend
```

The default Java run skips real-database integration tests. See [testing and evaluation](docs/EVALUATION.md) for running those against a **disposable** test database, browser tests, and what has actually been verified.

## Documentation

- [Project requirements and walkthrough](docs/PROJECT_REQUIREMENTS.md)
- [Architecture and RAG flow](docs/ARCHITECTURE.md)
- [API and error reference](docs/API.md)
- [Testing and evaluation](docs/EVALUATION.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Learning guide](docs/LEARNING_GUIDE.md)
- [Frontend development](frontend/README.md)
