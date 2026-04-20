CREATE TABLE password_reset_tokens (
    token_hash  VARCHAR(64) PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_pwreset_user_active
    ON password_reset_tokens (user_id)
    WHERE used_at IS NULL;
