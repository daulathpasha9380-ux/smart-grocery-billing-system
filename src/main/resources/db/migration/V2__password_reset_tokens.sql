CREATE TABLE password_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_password_reset_tokens_username
    ON password_reset_tokens(username, created_at);
