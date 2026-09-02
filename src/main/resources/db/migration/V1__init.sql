-- 소때잡 초기 스키마. 정본: myDocs/04_데이터모델_ERD.md v1.3 §1.
-- Flyway forward-only — 적용된 파일은 고치지 않고 다음 V 번호를 추가한다.
-- financial_chunks(pgvector)는 P2 착수 시 V3에서 (E-21).

CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    auth_provider         VARCHAR(20)  NOT NULL DEFAULT 'LOCAL'
                          CHECK (auth_provider IN ('LOCAL', 'KAKAO', 'NAVER', 'GOOGLE')),
    provider_user_id      VARCHAR(255),
    monthly_budget        INTEGER,
    outlier_threshold     DOUBLE PRECISION,
    avg_satisfaction      DOUBLE PRECISION,
    retrospect_delay_days INTEGER      NOT NULL DEFAULT 1,
    onboarding_completed  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);
-- 소셜 식별자는 (provider, provider_user_id)다. 같은 이메일이라는 이유로 계정을 합치지 않는다.
CREATE UNIQUE INDEX uq_users_provider ON users (auth_provider, provider_user_id) WHERE provider_user_id IS NOT NULL;

CREATE TABLE behavior_clusters (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users (id),
    -- `카테고리|시간대|목적|동행인` — 시간대는 식사 카테고리에만 (B-10)
    cluster_key           VARCHAR(255) NOT NULL,
    display_name          VARCHAR(50),
    parent_id             BIGINT       REFERENCES behavior_clusters (id),
    retrospect_count      INTEGER      NOT NULL DEFAULT 0,
    raw_average           DOUBLE PRECISION,
    adjusted_satisfaction DOUBLE PRECISION,
    -- 절감액 계산 전용. 지도 가로축은 monthly_total_amount다.
    avg_amount            INTEGER,
    monthly_total_amount  INTEGER,
    analysis_year_month   CHAR(7),
    tx_count              INTEGER,
    burden_ratio          DOUBLE PRECISION,
    evaluation_status     VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                          CHECK (evaluation_status IN ('RESOLVED', 'PENDING')),
    quadrant              VARCHAR(10)  CHECK (quadrant IN ('PROTECT', 'KEEP', 'MINOR', 'PRIORITY')),
    verdict               VARCHAR(10)  CHECK (verdict IN ('SUSTAIN', 'ADJUST')),
    CONSTRAINT uq_behavior_clusters_key UNIQUE (user_id, cluster_key),
    -- PENDING이면 quadrant·verdict는 null (E-11)
    CONSTRAINT ck_behavior_clusters_pending
        CHECK (evaluation_status = 'RESOLVED' OR (quadrant IS NULL AND verdict IS NULL))
);

CREATE TABLE transactions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id),
    occurred_at         TIMESTAMPTZ  NOT NULL,
    merchant            VARCHAR(255) NOT NULL,
    merchant_normalized VARCHAR(255),
    amount              INTEGER      NOT NULL,
    category            VARCHAR(50)  NOT NULL,
    source_category     VARCHAR(100),
    card_issuer         VARCHAR(10)  NOT NULL CHECK (card_issuer IN ('KB', 'HANA', 'SHINHAN')),
    time_slot           VARCHAR(10)  NOT NULL CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'NIGHT')),
    behavior_id         BIGINT       REFERENCES behavior_clusters (id),
    -- userId + occurredAt + merchant + amount 해시 (04 §4 중복 판별)
    import_hash         VARCHAR(64)  NOT NULL,
    CONSTRAINT uq_transactions_import_hash UNIQUE (user_id, import_hash)
);
CREATE INDEX ix_transactions_user_occurred ON transactions (user_id, occurred_at);
CREATE INDEX ix_transactions_behavior ON transactions (behavior_id);

CREATE TABLE retrospects (
    id             BIGSERIAL PRIMARY KEY,
    -- 1:1 보장. 중복 저장은 409 DUPLICATE_RETROSPECT
    transaction_id BIGINT      NOT NULL UNIQUE REFERENCES transactions (id),
    satisfaction   VARCHAR(10) NOT NULL CHECK (satisfaction IN ('HIGH', 'LOW', 'UNKNOWN')),
    -- 표준 태그 7/6종 중 사용자가 확인한 값만. 자유 문자열은 400 INVALID_TAG (E-20)
    purpose        VARCHAR(20),
    purpose_raw    TEXT,
    companion      VARCHAR(20),
    companion_raw  TEXT,
    repeat_intent  BOOLEAN,
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
    source         VARCHAR(10) NOT NULL CHECK (source IN ('CANDIDATE', 'ONBOARDING', 'MANUAL')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE goals (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    name           VARCHAR(50) NOT NULL,
    target_amount  INTEGER     NOT NULL,
    current_amount INTEGER     NOT NULL DEFAULT 0,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_goals_user ON goals (user_id) WHERE deleted_at IS NULL;

CREATE TABLE suggestions (
    id             BIGSERIAL PRIMARY KEY,
    behavior_id    BIGINT      NOT NULL REFERENCES behavior_clusters (id),
    adjust_count   INTEGER,
    -- avg_amount × adjust_count
    expected_saving INTEGER,
    goal_id        BIGINT      REFERENCES goals (id),
    status         VARCHAR(10) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'ADOPTED', 'REJECTED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE monthly_snapshots (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT  NOT NULL REFERENCES users (id),
    year_month        CHAR(7) NOT NULL,
    total_spending    INTEGER,
    unsatisfied_count INTEGER,
    repeat_count      INTEGER,
    saved_amount      INTEGER,
    CONSTRAINT uq_monthly_snapshots UNIQUE (user_id, year_month)
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    type       VARCHAR(20) NOT NULL CHECK (type IN ('RETROSPECT_DUE', 'SUGGESTION')),
    ref_id     BIGINT,
    message    TEXT        NOT NULL,
    is_read    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE is_read = FALSE;

-- 데모 계정 폴백 (E-15). 익명화 거래 시드는 V2__seed_demo.sql (정민규).
INSERT INTO users (email, auth_provider, retrospect_delay_days, onboarding_completed)
VALUES ('demo@sottaejap.kr', 'LOCAL', 1, FALSE);
