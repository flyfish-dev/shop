ALTER TABLE shop_order ADD COLUMN IF NOT EXISTS sales_channel VARCHAR(30) DEFAULT 'WEBSITE';
ALTER TABLE shop_order ADD COLUMN IF NOT EXISTS channel_order_key VARCHAR(80);
ALTER TABLE shop_order ADD COLUMN IF NOT EXISTS refund_state VARCHAR(30) DEFAULT 'NONE';
ALTER TABLE shop_order ADD COLUMN IF NOT EXISTS refund_amount DECIMAL(18,2) DEFAULT 0;
ALTER TABLE shop_order ADD COLUMN IF NOT EXISTS channel_refund_revision BIGINT DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_shop_channel_order ON shop_order(channel_order_key);
CREATE TABLE IF NOT EXISTS shop_delivery_task (
    order_no VARCHAR(80) NOT NULL, task_key VARCHAR(180) NOT NULL,
    status VARCHAR(30) NOT NULL, result_message VARCHAR(500),
    lease_until BIGINT NOT NULL, updated_at BIGINT NOT NULL,
    PRIMARY KEY (order_no, task_key)
);
ALTER TABLE shop_delivery_task ADD COLUMN IF NOT EXISTS retry_safe BOOLEAN DEFAULT FALSE;
ALTER TABLE shop_delivery_task ADD COLUMN IF NOT EXISTS lease_token VARCHAR(80);
CREATE TABLE IF NOT EXISTS shop_delivery_resolution (
    resolution_id VARCHAR(80) PRIMARY KEY, order_no VARCHAR(80) NOT NULL,
    task_key VARCHAR(180) NOT NULL, actor VARCHAR(80) NOT NULL,
    resolution VARCHAR(30) NOT NULL, evidence VARCHAR(1000) NOT NULL,
    created_at BIGINT NOT NULL
);
