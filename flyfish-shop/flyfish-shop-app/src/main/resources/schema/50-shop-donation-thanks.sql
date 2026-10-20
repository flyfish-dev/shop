CREATE TABLE IF NOT EXISTS `shop_donation_thank_you`
(
    `id`                bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`          varchar(64) NOT NULL COMMENT '打赏订单号及通知幂等键',
    `state`             varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '待处理、处理中、已发送或已转工单',
    `attempts`          int         NOT NULL DEFAULT 0 COMMENT '处理次数',
    `next_attempt_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试或租约失效时间',
    `claim_token`       varchar(36) NULL COMMENT '处理租约标识',
    `last_error`        varchar(128) NULL COMMENT '最近错误类别，不保存敏感正文',
    `ticket_no`         varchar(64) NULL COMMENT '人工感谢跟进工单编号',
    `completed_time`    datetime    NULL COMMENT '通知完成时间',
    `create_by`         varchar(32) NOT NULL COMMENT '创建人',
    `update_by`         varchar(32) NOT NULL COMMENT '修改人',
    `create_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`         boolean     NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`order_no`),
    INDEX `idx_donation_thanks_due` (`state`, `next_attempt_time`, `id`)
);
