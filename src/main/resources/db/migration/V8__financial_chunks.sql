-- 금융 RAG 저장소 (FR-12 · E-21 · 04 §1). 스키마 소유자는 server이므로 확장과 테이블은
-- 여기서 만든다 — ai/는 이미 있는 테이블을 읽기만 한다 (ai/app/rag/retriever.py).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE financial_chunks (
    id        BIGSERIAL PRIMARY KEY,
    chunk_id  VARCHAR(255) NOT NULL,
    content   TEXT         NOT NULL,
    source    VARCHAR(255) NOT NULL,
    metadata  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 1536 = text-embedding-3-small. ai/app/rag/embedding.py의 EMBEDDING_DIMENSIONS와 같아야 한다.
    embedding VECTOR(1536) NOT NULL,
    -- 재적재가 ON CONFLICT (chunk_id) DO UPDATE로 도는 전제다 (scripts/ingest_financial_docs.py).
    CONSTRAINT uq_financial_chunks_chunk_id UNIQUE (chunk_id)
);

-- 벡터 인덱스(hnsw · ivfflat)는 넣지 않는다. 데모 규모(수백 청크)에서는 순차 스캔이 더 빠르고,
-- ivfflat의 lists 값은 데이터가 쌓인 뒤에야 정할 수 있다. 느려지면 그때 추가한다.
