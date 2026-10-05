# Project requirements and walkthrough

**AI Knowledge Assistant — Knowledge Desk**, a local portfolio project by Sajad Ali.

## 1. The problem

A folder of policies, notes and runbooks can be difficult to search manually. This project lets someone upload documents and ask questions about them. It retrieves relevant passages and gives the model those passages as reference material. The reader can inspect the supporting text.

This approach is called **retrieval-augmented generation (RAG)**: retrieve context first, then generate an answer. Uploading does not train or fine-tune the model.

## 2. What the application must do

| ID | Requirement | Acceptance check |
|---|---|---|
| FR-01 | Accept TXT, Markdown and text-based PDF. | A supported sample upload appears in the library with a passage count. |
| FR-02 | Extract and split readable text. | Long documents produce multiple passages; PDF citations retain page numbers. |
| FR-03 | Create real embeddings using Spring AI and Ollama. | Upload invokes the embedding endpoint; unavailable embeddings produce an error. |
| FR-04 | Persist text and vectors in a vector database. | Documents remain after the backend restarts while the PostgreSQL volume remains. |
| FR-05 | Retrieve relevant passages for a question. | Vector search returns up to five results above the configured threshold. |
| FR-06 | Support all-document and single-document search. | Selecting one document restricts retrieval to that document's UUID. |
| FR-07 | Generate answers with citations. | The answer includes references matching the returned citation list and retrieved passages. |
| FR-08 | Allow evidence inspection without generation. | Sources only displays passages and makes no chat-model request. Embedding search still requires Ollama. |
| FR-09 | Decline insufficient evidence. | Empty corpus/no qualifying passages skips generation; a model abstention produces a fixed no-evidence message. |
| FR-10 | Deduplicate identical uploads. | Uploading the same bytes again returns the existing document. |
| FR-11 | Delete a document and all its vectors. | Confirming deletion removes its document row and associated chunk rows. |
| FR-12 | Export the current result. | Export JSON includes answer metadata, citations and source excerpts. |

## 3. Reliability requirements

| ID | Requirement | Implementation |
|---|---|---|
| NFR-01 | Avoid unbounded model waiting. | Caller deadline, HTTP timeout, finite output budget. |
| NFR-02 | Avoid parallel model overload. | One admitted AI operation; further requests receive 429. |
| NFR-03 | Avoid partially indexed documents. | Generate embeddings first; insert metadata and chunks together in a database transaction. |
| NFR-04 | Validate input and output. | File limits, question limits, embedding dimensions, structured model response and citation checks. |
| NFR-05 | Explain provenance. | Filename, passage ordinal, optional PDF page, retrieved text and similarity. |
| NFR-06 | Be reproducible. | Lockfiles, Maven wrapper, Compose database configuration and automated tests. |
| NFR-07 | Support small screens. | Responsive library, question form, answer and source panels. |

A valid citation number does not prove that its passage supports the answer. Semantic grounding still requires evaluation and human review.

## 4. Understand the main concepts

| Term | Plain-English meaning in this project |
|---|---|
| Document | One uploaded file, represented by its name, identifier and passages. |
| Chunk/passage | A smaller piece of extracted text. We retrieve pieces instead of sending every document to the model. |
| Embedding | A list of 768 numbers produced by a model to represent text for similarity search. |
| Vector database | PostgreSQL with pgvector, which can store and compare these number lists. |
| Cosine similarity | A way of comparing vector directions. It is a retrieval score, not the probability an answer is correct. |
| Context | Retrieved passages placed in the generation prompt. |
| Prompt | Instructions and reference material sent to the model. |
| Citation | A reference such as `[1]` pointing to a returned passage. |
| Hallucination | Generated content that is incorrect or unsupported. RAG reduces some risks but does not eliminate them. |
| Transaction | A database operation that either commits the complete document and chunks or rolls them back. |

## 5. Follow an upload

1. Choose `examples/incident-runbook.md` in the browser.
2. React sends its bytes as a multipart request to `POST /api/documents`.
3. `KnowledgeController` passes the filename and bytes to `DocumentService`.
4. The service admits one operation, checks duplicate hashes and corpus limits, and calls `TextExtractor`.
5. The extractor reads text and forms overlapping passages. PDF passages retain their page numbers.
6. `AiClient` calls Spring AI's `EmbeddingModel` in batches of up to eight passages. Ollama returns the vectors.
7. The backend checks that each vector has 768 finite values and a nonzero norm.
8. `KnowledgeStore` inserts document metadata and passage vectors in one PostgreSQL transaction.
9. React refreshes the library and shows the indexed passage count.

The raw PDF or text upload is not saved as a file on the server. Its extracted passages and metadata are saved in the database.

## 6. Follow a question

Example: “How often should incident updates be posted?”

1. React sends the question, optional selected document ID and mode to `POST /api/questions`.
2. `RagService` validates the request and checks the library/model compatibility.
3. `AiClient` embeds the question using the same embedding model as the documents.
4. `KnowledgeStore` runs a parameterized pgvector cosine-distance query, optionally filtered by document ID.
5. Results below the threshold are removed. At most five qualifying passages are returned.
6. If no passages qualify, Java returns `NO_EVIDENCE` without asking the chat model.
7. In **Sources only** mode, Java returns the retrieved passages immediately.
8. In **AI answer** mode, the question and numbered passages go to `ChatClient`. The model is instructed to use only that material and decline unsupported questions.
9. Java checks the structured answer, citation ranges and matching inline citation numbers. Invalid references produce an explicit error.
10. React displays the answer and passages. Clicking `[1]` highlights source 1.

The example runbook says updates should be posted every 30 minutes. A correct live-model answer should say this and cite the supporting passage. An unrelated question should be declined, but model behavior must be checked rather than assumed.

## 7. How this differs from AI Developer Agent

| Project | Source of context | Main AI behavior |
|---|---|---|
| Incident Desk | Predefined fictional service health, logs and deployments | Fixed incident analysis plus a separate tool-calling lab |
| Knowledge Desk | Text uploaded by the user and retrieved using embeddings | RAG document question answering |

Knowledge Desk does not call Incident Desk, use MCP, or connect to live service monitoring. The sample documents are fictional; your uploaded documents provide the actual retrieval corpus.

## 8. Read the implementation in order

Java files are under `src/main/java/com/sajad/knowledge/`.

1. `api/KnowledgeController.java`: HTTP entry points and request fields.
2. `document/TextExtractor.java`: text extraction and overlapping chunks.
3. `document/DocumentService.java`: duplicate checks, embeddings and upload workflow.
4. `store/KnowledgeStore.java` and `src/main/resources/schema.sql`: document ownership, transactions and vector queries.
5. `rag/AiClient.java`: embedding requests and generation prompt.
6. `rag/RagService.java`: retrieval, abstention, citation checks and response construction.
7. `config/WorkGate.java`: concurrency admission and deadlines.
8. `frontend/src/main.jsx`: upload, selection, question submission and source rendering.

## 9. What is outside this version?

Authentication, multiple user workspaces, OCR, DOCX, website crawling, external integrations, chat memory, fine-tuning and production deployment are not implemented. The exact vector search suits a small local corpus; a large corpus would need evaluation of indexing, access control, batching, background jobs and operational monitoring.

Suggested next learning exercise: upload two short documents with conflicting policies. Ask the same question with and without a document filter. Inspect which passages are returned and whether the model acknowledges the conflict.
