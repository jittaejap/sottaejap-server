-- 대화형 회고의 최근 대화 (05 §3 recent_messages). 04 ERD에는 없던 테이블이다 — AI가 직전 문맥을
-- 알아야 다음 질문을 만들 수 있는데, 클라이언트가 이력을 들고 다니면 새로고침에 사라진다.
-- 전체 이력을 AI에 보내지는 않는다. 여기 쌓아두고 최근 몇 건만 실어 보낸다.
CREATE TABLE chat_messages (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    -- 어느 거래에 대한 회고 대화인지. 거래에 매이지 않는 대화(금융 Q&A 등)는 NULL이다.
    transaction_id BIGINT      REFERENCES transactions (id),
    role           VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content        TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 최근 대화를 거래별로 뒤에서부터 읽는다. 질문과 답변은 같은 created_at을 갖기 때문에 id까지 넣어야
-- 정렬이 한 가지로 정해진다 (조회도 created_at DESC, id DESC로 읽는다).
CREATE INDEX ix_chat_messages_user_tx_created
    ON chat_messages (user_id, transaction_id, created_at DESC, id DESC);
