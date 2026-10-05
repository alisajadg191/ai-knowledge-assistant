# HTTP API

Base URL: `http://127.0.0.1:8081`. No authentication; local single-user use only.

| Method and route | Request | Result |
|---|---|---|
| `GET /api/status` | None | Backend/database status, document count, busy flag, configured model names. Does not probe model readiness. |
| `GET /api/documents` | None | Document IDs, filenames, chunk counts and creation times. |
| `POST /api/documents` | Multipart field `file` | `{document, duplicate}`; 200 for new or existing identical file. |
| `DELETE /api/documents/{id}` | UUID path parameter | Deletes document and chunks; returns `{status:"deleted"}`. |
| `POST /api/questions` | JSON below | Answer status, text, citations, sources, timing and warning. |

```bash
curl http://127.0.0.1:8081/api/documents \
  -F file=@examples/incident-runbook.md

curl http://127.0.0.1:8081/api/questions \
  -H 'Content-Type: application/json' \
  -d '{"question":"How often should incident updates be posted?","mode":"answer"}'
```

Question fields:

- `question`: nonblank string, at most 2,000 characters.
- `documentId`: optional UUID; omitted/null searches all documents.
- `mode`: `answer` (default) or `sources`.

Response `status` is `ANSWERED`, `SOURCES_ONLY` or `NO_EVIDENCE`. Sources contain a reference number, document UUID, filename, passage ordinal, optional PDF page, cosine similarity and the exact retrieved excerpt. `citations` identifies references used by a generated answer. Retrieved but uncited sources are labelled accordingly in the UI.

A `NO_EVIDENCE` answer can still include retrieved passages when the model judged them insufficient. Empty retrieval and model abstention are different reasons for that status.

## Errors

Errors use Spring Problem Detail JSON with `status`, `title`, `detail` and applicable `instance`/application `code` fields.

| HTTP status | Examples |
|---|---|
| 400 | Missing file, blank/oversized question, malformed JSON/UUID, unreadable file |
| 404 | Document no longer exists |
| 409 | Corpus limit or embedding model-name mismatch |
| 413 | Multipart/file text limits exceeded |
| 415 | Unsupported extension |
| 422 | No readable text, such as an image-only PDF |
| 429 | Another gated operation is running |
| 502 | Model failure, invalid embeddings, unusable structured response or citations |
| 503 | Database unavailable, interrupted worker or shutdown |
| 504 | Caller deadline exceeded |

No model error is silently converted into a successful AI answer. After an upload timeout, refresh documents before retrying because commit outcome can be ambiguous near the deadline.
