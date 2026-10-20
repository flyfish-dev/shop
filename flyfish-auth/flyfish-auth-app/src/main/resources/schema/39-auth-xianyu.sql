CREATE TABLE IF NOT EXISTS auth_xianyu_link (
    code_hash VARCHAR(64) PRIMARY KEY, target_user_id BIGINT NOT NULL,
    subject VARCHAR(80), expires_at BIGINT NOT NULL, used BOOLEAN NOT NULL,
    pending TEXT, confirmed BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS auth_xianyu_subject (
    subject VARCHAR(80) PRIMARY KEY, created_at BIGINT NOT NULL
);
