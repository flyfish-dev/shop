CREATE TABLE IF NOT EXISTS xy_seller (
    seller_key VARCHAR(80) PRIMARY KEY, application VARCHAR(20) NOT NULL,
    token_cipher TEXT NOT NULL, expires_at BIGINT NOT NULL, enabled BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_seller_identity (
    seller_key VARCHAR(80) PRIMARY KEY, user_hash VARCHAR(64) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS xy_media (
    media_id VARCHAR(80) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_capability (
    capability_key VARCHAR(180) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL,
    operation VARCHAR(40) NOT NULL, granted BOOLEAN NOT NULL, verified BOOLEAN NOT NULL,
    expires_at BIGINT NOT NULL, request_key VARCHAR(120), topics TEXT, category_ids TEXT,
    updated_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_listing (
    listing_key VARCHAR(80) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL,
    local_item_id VARCHAR(40) NOT NULL, remote_item_id VARCHAR(80),
    revision BIGINT NOT NULL, status VARCHAR(40) NOT NULL, native_enabled BOOLEAN NOT NULL,
    payload TEXT NOT NULL, remote_snapshot TEXT, updated_at BIGINT NOT NULL
);
ALTER TABLE xy_capability ADD COLUMN IF NOT EXISTS application VARCHAR(20) DEFAULT 'commerce';
CREATE UNIQUE INDEX IF NOT EXISTS uk_xy_capability_application ON xy_capability(application,seller_key,operation);
CREATE TABLE IF NOT EXISTS xy_verification (
    capability_key VARCHAR(180) PRIMARY KEY, actor VARCHAR(80) NOT NULL,
    evidence VARCHAR(1000) NOT NULL, verified_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_listing_revision (
    listing_key VARCHAR(80) NOT NULL, revision BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL, payload TEXT NOT NULL,
    remote_snapshot TEXT NOT NULL, changed_at BIGINT NOT NULL,
    PRIMARY KEY (listing_key,revision)
);
CREATE TABLE IF NOT EXISTS xy_publish_resolution (
    listing_key VARCHAR(80) PRIMARY KEY, actor VARCHAR(80) NOT NULL,
    remote_item_id VARCHAR(80) NOT NULL, evidence VARCHAR(1000) NOT NULL, resolved_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_order (
    order_key VARCHAR(80) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL,
    local_order_no VARCHAR(80), remote_order_id VARCHAR(80), buyer_subject VARCHAR(80),
    channel VARCHAR(30) NOT NULL, amount_cents BIGINT NOT NULL,
    creation_state VARCHAR(30) NOT NULL, platform_state INT NOT NULL,
    refund_state VARCHAR(30) NOT NULL, refund_cents BIGINT NOT NULL,
    fulfillment_state VARCHAR(30) NOT NULL, shipment_state VARCHAR(30) NOT NULL,
    snapshot TEXT, revision BIGINT NOT NULL, expires_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
    UNIQUE (seller_key, remote_order_id), UNIQUE (local_order_no)
);
CREATE TABLE IF NOT EXISTS xy_job (
    job_key VARCHAR(180) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL, kind VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL, status VARCHAR(30) NOT NULL, attempts INT NOT NULL,
    next_at BIGINT NOT NULL, lease_until BIGINT NOT NULL, lease_token VARCHAR(80),
    last_error VARCHAR(200), created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_message (
    message_key VARCHAR(180) PRIMARY KEY, seller_key VARCHAR(80) NOT NULL,
    topic VARCHAR(160) NOT NULL, payload TEXT NOT NULL, created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_identity (
    identity_key VARCHAR(180) PRIMARY KEY, subject VARCHAR(80) NOT NULL UNIQUE,
    token_cipher TEXT NOT NULL, application VARCHAR(20) NOT NULL, expires_at BIGINT NOT NULL,
    portal_token_cipher TEXT, created_at BIGINT NOT NULL
);
ALTER TABLE xy_message ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'RECEIVED';
ALTER TABLE xy_message ADD COLUMN IF NOT EXISTS last_error VARCHAR(80);
CREATE TABLE IF NOT EXISTS xy_ticket (
    ticket_hash VARCHAR(64) PRIMARY KEY, subject VARCHAR(80) NOT NULL,
    expires_at BIGINT NOT NULL, used BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_session (
    session_hash VARCHAR(64) PRIMARY KEY, subject VARCHAR(80) NOT NULL,
    expires_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS xy_audit (
    audit_key VARCHAR(80) PRIMARY KEY, actor VARCHAR(80) NOT NULL,
    operation VARCHAR(80) NOT NULL, resource_key VARCHAR(180) NOT NULL, created_at BIGINT NOT NULL
);
