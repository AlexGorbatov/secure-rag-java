CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
                              id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              content    TEXT,
                              metadata   JSON,
                              embedding  VECTOR(384)
);

CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE vector_store IS 'Document chunks with embeddings; metadata carries document_id, owner and allowed_groups';