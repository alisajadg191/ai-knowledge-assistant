# Learn the project gradually

First follow the [requirements walkthrough](PROJECT_REQUIREMENTS.md), then run the included example. You do not need to understand every class on your first reading.

## Five small study sessions

1. **HTTP and documents:** trace an upload from React to `KnowledgeController`. Explain multipart upload versus JSON requests. Read `TextExtractor` and inspect its PDF tests.
2. **Embeddings:** find `embedDocuments` and `embedQuestion`. Explain why ingestion and search use the same model. An embedding represents text numerically; it is not a generated answer.
3. **SQL and retrieval:** read the schema, foreign key and `KnowledgeStore.search`. Explain cosine distance, top-k and threshold. Change the threshold on a small sample and inspect results.
4. **RAG generation:** read `RagService.ask` and the `AiClient` prompt. Compare Sources only with AI answer. Explain why valid JSON/citations can still contain incorrect claims.
5. **Reliability and UI:** read the gate, error handler and tests, then follow React loading/error state. Explain why a timeout does not necessarily stop remote computation immediately.

## Exercises

- Upload two documents and check a single-document filter.
- Upload identical bytes under a different filename and explain deduplication.
- Upload a PDF and find a passage's page number.
- Ask an unsupported question, then compare the passages with the model's decision.
- Stop Ollama and verify the visible failure. Existing document listing should still work while PostgreSQL runs.
- Read the transaction rollback test and explain why embeddings happen before database writes.

## Honest project explanation

“This is a local document Q&A portfolio project using Java, Spring Boot, Spring AI, React and pgvector. It extracts and chunks text, creates Ollama embeddings, retrieves similar passages and asks a model to generate a cited answer. It validates citation references and handles insufficient context and model failures, while showing the original excerpts for verification. I developed it with AI assistance and am studying the implementation and its trade-offs.”

Do not claim production use, model training or guaranteed hallucination prevention. Build your understanding by modifying a small part and explaining its tests.

## Primary references

- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Ollama embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html)
- [pgvector](https://github.com/pgvector/pgvector)
- [Ollama documentation](https://docs.ollama.com/)
- [React learning guide](https://react.dev/learn)
