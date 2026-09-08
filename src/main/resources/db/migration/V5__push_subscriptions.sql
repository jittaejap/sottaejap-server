-- Web Push 구독 (FR-10 확장). 브라우저가 만든 endpoint가 구독의 고유 식별자다.
-- 한 사용자가 기기·브라우저마다 하나씩 갖는다.
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    endpoint   TEXT         NOT NULL UNIQUE,
    p256dh     VARCHAR(255) NOT NULL,
    auth       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);
