import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    signal: AbortSignal.timeout(130000),
  });
  const data = await response.json();
  if (!response.ok)
    throw new Error(data.detail || "Request failed. Check the backend logs.");
  return data;
}
function App() {
  const [documents, setDocuments] = useState([]),
    [status, setStatus] = useState(null);
  const [selected, setSelected] = useState(""),
    [question, setQuestion] = useState("");
  const [mode, setMode] = useState("answer"),
    [busy, setBusy] = useState(""),
    [error, setError] = useState("");
  const [notice, setNotice] = useState(""),
    [result, setResult] = useState(null),
    [activeSource, setActiveSource] = useState(null);
  const [pendingDelete, setPendingDelete] = useState(null);
  const fileInput = useRef(null);
  async function refresh() {
    const [docs, s] = await Promise.all([
      api("/api/documents"),
      api("/api/status"),
    ]);
    setDocuments(docs);
    setStatus(s);
  }
  useEffect(() => {
    refresh().catch(() =>
      setError(
        "Backend is unavailable. Start PostgreSQL and Spring Boot, then click Refresh.",
      ),
    );
  }, []);
  function failure(e) {
    setError(
      e.name === "TimeoutError"
        ? "The request timed out. Wait and refresh the library before retrying; work may still be finishing."
        : e.message,
    );
  }
  async function upload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    setError("");
    setNotice("");
    if (file.size > 5 * 1024 * 1024) {
      setError("Choose a file no larger than 5 MB.");
      event.target.value = "";
      return;
    }
    setBusy("upload");
    try {
      const form = new FormData();
      form.append("file", file);
      const data = await api("/api/documents", { method: "POST", body: form });
      await refresh();
      setNotice(
        data.duplicate
          ? "This file is already in your library."
          : `${data.document.name} indexed into ${data.document.chunks} passages.`,
      );
    } catch (e) {
      failure(e);
    } finally {
      setBusy("");
      event.target.value = "";
    }
  }
  async function remove() {
    const doc = pendingDelete;
    if (!doc) return;
    setBusy("delete");
    setError("");
    try {
      await api(`/api/documents/${doc.id}`, { method: "DELETE" });
      if (selected === doc.id) setSelected("");
      setResult(null);
      setPendingDelete(null);
      await refresh();
      setNotice("Document and its stored passages were deleted.");
    } catch (e) {
      failure(e);
    } finally {
      setBusy("");
    }
  }
  async function ask(event) {
    event.preventDefault();
    if (busy) return;
    setBusy("ask");
    setError("");
    setNotice("");
    setResult(null);
    setActiveSource(null);
    try {
      const data = await api("/api/questions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question, documentId: selected || null, mode }),
      });
      setResult(data);
    } catch (e) {
      failure(e);
    } finally {
      setBusy("");
    }
  }
  function exportAnswer() {
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(result, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "knowledge-answer.json";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  function answerText(text) {
    return text.split(/(\[\d+\])/g).map((part, i) => {
      const match = part.match(/^\[(\d+)\]$/);
      return match && result.citations.includes(Number(match[1])) ? (
        <button
          key={i}
          className="citation"
          onClick={() => {
            setActiveSource(Number(match[1]));
            document
              .getElementById(`source-${match[1]}`)
              ?.scrollIntoView({ behavior: "smooth", block: "nearest" });
          }}
          aria-label={`View source ${match[1]}`}
        >
          {part}
        </button>
      ) : (
        <React.Fragment key={i}>{part}</React.Fragment>
      );
    });
  }
  return (
    <div className="app">
      <aside className="library">
        <a href="/" className="brand">
          <span className="brand-mark">
            K<span>·</span>
          </span>
          <span>
            Knowledge Desk<small>YOUR DOCUMENTS, CONNECTED</small>
          </span>
        </a>
        <div className="library-title">
          <span>DOCUMENT LIBRARY</span>
          <span className="count">{documents.length}</span>
        </div>
        <button
          className="upload"
          disabled={!!busy}
          onClick={() => fileInput.current?.click()}
        >
          <span>＋</span>
          {busy === "upload" ? "Indexing document…" : "Upload a document"}
        </button>
        <input
          ref={fileInput}
          aria-label="Upload document"
          className="file-input"
          type="file"
          accept=".pdf,.txt,.md"
          onChange={upload}
          disabled={!!busy}
        />
        <p className="upload-hint">
          PDF, Markdown or text · up to 5 MB
          <br />
          Text-based PDFs only. Embeddings require Ollama.
        </p>
        <button
          className={"all-docs " + (!selected ? "chosen" : "")}
          disabled={!!busy}
          onClick={() => setSelected("")}
        >
          <span>▦</span> All documents <span>{documents.length}</span>
        </button>
        <div className="document-list">
          {documents.length ? (
            documents.map((doc) => (
              <div
                className={
                  "document " + (selected === doc.id ? "selected" : "")
                }
                key={doc.id}
              >
                <button
                  className="doc-select"
                  disabled={!!busy}
                  onClick={() => setSelected(doc.id)}
                >
                  <span className="file-icon">
                    {doc.name.split(".").pop().toUpperCase()}
                  </span>
                  <span className="doc-name">
                    {doc.name}
                    <small>
                      {doc.chunks} passages ·{" "}
                      {new Date(doc.createdAt).toLocaleDateString()}
                    </small>
                  </span>
                </button>
                <button
                  className="delete"
                  disabled={!!busy}
                  aria-label={`Delete ${doc.name}`}
                  onClick={() => setPendingDelete(doc)}
                >
                  ×
                </button>
              </div>
            ))
          ) : (
            <div className="library-empty">
              <span>▤</span>
              <p>Your library starts here.</p>
              <small>Upload a document to give your questions a source.</small>
            </div>
          )}
        </div>
        {pendingDelete && (
          <div className="delete-confirm" role="alert">
            <strong>Delete {pendingDelete.name}?</strong>
            <p>This removes its text and vectors from this workspace.</p>
            <button disabled={!!busy} onClick={remove}>
              Delete document
            </button>
            <button disabled={!!busy} onClick={() => setPendingDelete(null)}>
              Cancel
            </button>
          </div>
        )}
        <div className="library-bottom">
          <span className="local-dot" /> Local workspace
          <p>
            Documents stay in your PostgreSQL database. AI calls use the
            configured Ollama server.
          </p>
          <button
            disabled={!!busy}
            onClick={() => {
              setError("");
              refresh().catch(failure);
            }}
          >
            ↻ Refresh library
          </button>
        </div>
      </aside>
      <main>
        <header>
          <span>
            Workspace <span className="slash">/</span> Document Q&amp;A
          </span>
          <span className="status">
            <i className={status ? "up" : ""} />
            {status ? "Backend connected" : "Backend not connected"}
          </span>
        </header>
        <div className="workspace">
          <div className="intro">
            <div className="eyebrow">AI KNOWLEDGE ASSISTANT</div>
            <h1>Answers with a paper trail.</h1>
            <p>
              Ask your documents a question. Follow the answer back to its
              source.
            </p>
          </div>
          <div className="workspace-grid">
            <div className="conversation">
              {error && (
                <div role="alert" className="error">
                  <strong>Something needs your attention</strong>
                  <p>{error}</p>
                </div>
              )}
              {notice && (
                <div role="status" className="notice">
                  {notice}
                </div>
              )}
              <form className="question-panel" onSubmit={ask}>
                <div className="panel-heading">
                  <span className="step">01</span>
                  <h2>Ask a question</h2>
                  <span className="scope">
                    {selected ? "Selected document" : "Entire library"}
                  </span>
                </div>
                <label htmlFor="question">What would you like to know?</label>
                <textarea
                  id="question"
                  maxLength={2000}
                  required
                  value={question}
                  disabled={!!busy}
                  onChange={(e) => setQuestion(e.target.value)}
                  placeholder="What is the process for reporting an incident?"
                />
                <div className="question-actions">
                  <div className="mode" aria-label="Response mode">
                    <button
                      type="button"
                      disabled={!!busy}
                      aria-pressed={mode === "answer"}
                      className={mode === "answer" ? "active" : ""}
                      onClick={() => setMode("answer")}
                    >
                      AI answer
                    </button>
                    <button
                      type="button"
                      disabled={!!busy}
                      aria-pressed={mode === "sources"}
                      className={mode === "sources" ? "active" : ""}
                      onClick={() => setMode("sources")}
                    >
                      Sources only
                    </button>
                  </div>
                  <button
                    className="primary"
                    disabled={!!busy || !question.trim() || !documents.length}
                  >
                    {busy === "ask"
                      ? "Reading documents…"
                      : "Ask your documents →"}
                  </button>
                </div>
                <p className="form-hint">
                  {mode === "answer"
                    ? "Retrieves relevant passages, then asks the local model to answer with citations."
                    : "Runs embedding search and shows passages. No chat-model answer is generated."}
                </p>
              </form>
              <div aria-live="polite">
                {busy === "ask" ? (
                  <section className="answer-panel loading">
                    <div className="spinner" />
                    <h2>Finding the relevant passages</h2>
                    <p>
                      Retrieving evidence
                      {mode === "answer"
                        ? " and composing a grounded answer"
                        : ""}
                      . Local models may need a moment.
                    </p>
                  </section>
                ) : result ? (
                  <section className="answer-panel">
                    <div className="panel-heading">
                      <span className="step">02</span>
                      <h2>
                        {result.status === "ANSWERED"
                          ? "Answer"
                          : result.status === "SOURCES_ONLY"
                            ? "Search complete"
                            : "More evidence needed"}
                      </h2>
                      <button className="export" onClick={exportAnswer}>
                        Export JSON ↓
                      </button>
                    </div>
                    <span
                      className={
                        "result-label " +
                        (result.status === "ANSWERED" ? "answered" : "")
                      }
                    >
                      {result.status.replaceAll("_", " ")} · {result.durationMs}{" "}
                      ms
                    </span>
                    <p className="answered-question">{result.question}</p>
                    <p className="answer-text">{answerText(result.answer)}</p>
                    <p className="disclaimer">{result.warning}</p>
                  </section>
                ) : (
                  <section className="empty-answer">
                    <div className="book-icon">▤</div>
                    <h2>Your knowledge, easier to find.</h2>
                    <p>
                      Start with a policy, a runbook or a set of notes. Every
                      answer should lead you back to the passages behind it.
                    </p>
                    <div className="how">
                      <div>
                        <span>1</span>Upload
                      </div>
                      <div>
                        <span>2</span>Ask
                      </div>
                      <div>
                        <span>3</span>Verify
                      </div>
                    </div>
                  </section>
                )}
              </div>
              <div className="learning-note">
                <strong>What happens under the hood?</strong>
                <p>
                  Text is split into passages, converted into embeddings and
                  stored in pgvector. Your question retrieves similar passages,
                  which become the model's reference material.
                </p>
              </div>
            </div>
            <aside className="sources-panel">
              <div className="sources-heading">
                <h2>Source passages</h2>
                <span>{result?.sources.length || 0}</span>
              </div>
              <p className="sources-hint">
                Inspect the evidence. Similarity measures retrieval relevance,
                not answer confidence.
              </p>
              {result?.sources.length ? (
                result.sources.map((source) => (
                  <article
                    id={`source-${source.number}`}
                    key={source.number}
                    className={
                      "source " +
                      (activeSource === source.number ? "highlight" : "")
                    }
                  >
                    <div className="source-top">
                      <span className="source-number">[{source.number}]</span>
                      <span className="similarity">
                        {(source.similarity * 100).toFixed(1)}% similarity
                      </span>
                    </div>
                    <h3>{source.filename}</h3>
                    <p className="source-location">
                      {source.page ? `Page ${source.page} · ` : ""}Passage{" "}
                      {source.chunk}
                      {result.citations.includes(source.number)
                        ? " · Cited"
                        : " · Retrieved"}
                    </p>
                    <blockquote>{source.excerpt}</blockquote>
                  </article>
                ))
              ) : (
                <div className="sources-empty">
                  <span>⌕</span>
                  <p>
                    Evidence will appear here
                    <br />
                    after your first question.
                  </p>
                </div>
              )}
            </aside>
          </div>
          <footer>
            <span>Spring AI · Ollama · PostgreSQL / pgvector · React</span>
            <span>Built by Sajad Ali</span>
          </footer>
        </div>
      </main>
    </div>
  );
}
createRoot(document.getElementById("root")).render(<App />);
