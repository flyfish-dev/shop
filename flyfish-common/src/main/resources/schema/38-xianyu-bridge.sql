CREATE TABLE IF NOT EXISTS xianyu_bridge_nonce (
    nonce VARCHAR(80) PRIMARY KEY,
    expire_time BIGINT NOT NULL
);
