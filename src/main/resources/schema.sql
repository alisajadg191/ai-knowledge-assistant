CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS knowledge_document (
 id uuid PRIMARY KEY,
 name varchar(180) NOT NULL,
 content_hash varchar(64) NOT NULL UNIQUE,
 embedding_model varchar(200) NOT NULL,
 chunk_count integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS knowledge_chunk (
 id uuid PRIMARY KEY,
 document_id uuid NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
 ordinal integer NOT NULL,
 page integer,
 content text NOT NULL,
 embedding vector(768) NOT NULL,
 UNIQUE(document_id, ordinal)
);
CREATE INDEX IF NOT EXISTS knowledge_chunk_document_idx ON knowledge_chunk(document_id);
-- Exact cosine search suits this small portfolio corpus. No approximate index is required.
