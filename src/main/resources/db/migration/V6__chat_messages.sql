-- 금융 Q&A의 최근 대화 (05 §3 recent_messages). 04 ERD에는 없던 테이블이다 — 금융 Q&A는 서버가
-- 대화를 소유하기 때문이다 (E-67). 회고 대화는 반대로 클라이언트가 소유한다 — POST /retrospects/chat이
-- 상태 없는 프록시여서 step·확정값과 함께 recentMessages를 같이 보낸다 (E-63).
-- 전체 이력을 AI에 보내지는 않는다. 여기 쌓아두고 최근 6건만 실어 보낸다.
CREATE TABLE chat_messages (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    -- 어느 거래에 대한 대화인지. 금융 Q&A는 거래에 매이지 않으므로 NULL이다.
    -- 지금 이 테이블에 쌓이는 것은 금융 Q&A뿐이다 (회고 대화는 클라이언트 소유 — E-67).
    -- 서버가 회고 이력을 갖게 되면 그때 이 컬럼을 쓴다.
    transaction_id BIGINT      REFERENCES transactions (id),
    role           VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content        TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 최근 대화를 거래별로 뒤에서부터 읽는다. 질문과 답변은 같은 created_at을 갖기 때문에 id까지 넣어야
-- 정렬이 한 가지로 정해진다 (조회도 created_at DESC, id DESC로 읽는다).
CREATE INDEX ix_chat_messages_user_tx_created
    ON chat_messages (user_id, transaction_id, created_at DESC, id DESC);
