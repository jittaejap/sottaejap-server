-- 금융 RAG 저장소 (FR-12 · E-21 · 04 §1). 스키마 소유자는 server이므로 확장과 테이블은
-- 여기서 만든다 — ai는 이미 있는 테이블을 읽기만 한다.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE financial_chunks (
    id        BIGSERIAL PRIMARY KEY,
    chunk_id  VARCHAR(255) NOT NULL,
    content   TEXT         NOT NULL,
    source    VARCHAR(255) NOT NULL,
    metadata  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- 차원 1536(text-embedding-3-small)의 정본은 04 §1 · E-85다. ai가 적재를 붙일 때
    -- app/rag/embedding.py를 이 값에 맞춘다 — 그 파일은 아직 차원도 모델도 없는 스텁이다.
    -- 다른 차원을 고르면 재적재가 아니라 ALTER TABLE 마이그레이션이 하나 더 필요하다.
    embedding VECTOR(1536) NOT NULL,
    -- 재적재가 chunk_id로 제자리 갱신하도록 유일 제약을 건다. ai의 적재 스크립트가 이 제약에
    -- 기대어 ON CONFLICT (chunk_id) DO UPDATE를 쓴다 — 그 스크립트도 아직 구현 전이다.
    CONSTRAINT uq_financial_chunks_chunk_id UNIQUE (chunk_id)
);

-- 벡터 인덱스(hnsw · ivfflat)는 넣지 않는다. 데모 규모(수백 청크)에서는 순차 스캔이 더 빠르고,
-- ivfflat의 lists 값은 데이터가 쌓인 뒤에야 정할 수 있다. 느려지면 그때 추가한다.
