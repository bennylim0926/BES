CREATE TABLE account (
    account_id    BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    event_credits INT          NOT NULL DEFAULT 0,
    referral_code VARCHAR(20)  UNIQUE NOT NULL,
    referred_by   BIGINT       REFERENCES account(account_id),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE organiser_event (
    account_id BIGINT NOT NULL REFERENCES account(account_id) ON DELETE CASCADE,
    event_id   BIGINT NOT NULL REFERENCES event(event_id)   ON DELETE CASCADE,
    PRIMARY KEY (account_id, event_id)
);

CREATE TABLE session_token (
    token_id   VARCHAR(64)  PRIMARY KEY,
    role       VARCHAR(20)  NOT NULL,
    event_id   BIGINT       NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    judge_id   BIGINT       REFERENCES judge(judge_id) ON DELETE SET NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
