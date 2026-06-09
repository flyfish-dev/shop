CREATE TABLE IF NOT EXISTS `shop`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `name`        varchar(64)  NOT NULL COMMENT '商铺名称',
    `description` varchar(512) NOT NULL COMMENT '商铺描述',
    `avatar`      varchar(256) NULL COMMENT '商铺头像',
    `create_by`   varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)  NOT NULL COMMENT '修改人',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`name`)
);

CREATE TABLE IF NOT EXISTS `shop_item_group`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `shop_id`     bigint       NOT NULL COMMENT '所属店铺id',
    `name`        varchar(512) NOT NULL COMMENT '商品组名',
    `cover`       varchar(256) NULL COMMENT '商品组封面',
    `icon`        varchar(256) NULL COMMENT '商品组图标',
    `description` varchar(512) NULL COMMENT '商品组封面',
    `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled`     boolean      NOT NULL DEFAULT true COMMENT '启用状态',
    `create_by`   varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)  NOT NULL COMMENT '修改人',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`shop_id`, `name`)
);

ALTER TABLE `shop_item_group`
    ADD COLUMN IF NOT EXISTS `cover` varchar(256) NULL;
ALTER TABLE `shop_item_group`
    ADD COLUMN IF NOT EXISTS `icon` varchar(256) NULL;
ALTER TABLE `shop_item_group`
    ADD COLUMN IF NOT EXISTS `description` varchar(512) NULL;
ALTER TABLE `shop_item_group`
    ADD COLUMN IF NOT EXISTS `sort` int NOT NULL DEFAULT 0;
ALTER TABLE `shop_item_group`
    ADD COLUMN IF NOT EXISTS `enabled` boolean NOT NULL DEFAULT true;

CREATE TABLE IF NOT EXISTS `shop_item`
(
    `id`          bigint         NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `name`        varchar(64)    NOT NULL COMMENT '商品名称',
    `cover`       varchar(256)   NULL COMMENT '商品封面',
    `images`      varchar(1024)  NULL COMMENT '商品图集',
    `price`       decimal(10, 2) NOT NULL COMMENT '商品价格',
    `group_id`    bigint         NOT NULL COMMENT '商品分组id',
    `shop_id`     bigint         NOT NULL COMMENT '所属店铺id',
    `type`        varchar(32)    NOT NULL COMMENT '商品类型',
    `delivery_mode` varchar(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '交付方式',
    `tags`        varchar(256)   NOT NULL COMMENT '商品标签',
    `params`      json           NULL COMMENT '商品参数',
    `buy_count`   int            NOT NULL DEFAULT 0 COMMENT '购买人数',
    `description` text           NOT NULL COMMENT '商品描述',
    `sort`        int            NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled`     boolean        NOT NULL DEFAULT true COMMENT '上架状态',
    `pinned`      boolean        NOT NULL DEFAULT false COMMENT '置顶状态',
    `recommended` boolean        NOT NULL DEFAULT false COMMENT '推荐状态',
    `avatar`      varchar(256)   NULL COMMENT '商铺头像',
    `create_by`   varchar(32)    NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)    NOT NULL COMMENT '修改人',
    `create_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean        NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`shop_id`, `group_id`, `name`)
);

ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `cover` varchar(256) NULL;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `images` varchar(1024) NULL;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `shop_id` bigint NOT NULL DEFAULT 1;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `params` json NULL;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `buy_count` int NOT NULL DEFAULT 0;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `sort` int NOT NULL DEFAULT 0;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `enabled` boolean NOT NULL DEFAULT true;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `delivery_mode` varchar(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE `shop_item`
    MODIFY COLUMN `type` varchar(32) NOT NULL;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `pinned` boolean NOT NULL DEFAULT false;
ALTER TABLE `shop_item`
    ADD COLUMN IF NOT EXISTS `recommended` boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS `shop_order`
(
    `id`             bigint         NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `order_no`       varchar(64)    NOT NULL COMMENT '订单号',
    `item_id`        bigint         NOT NULL COMMENT '商品id',
    `shop_id`        bigint         NOT NULL COMMENT '所属店铺id',
    `buyer_id`       bigint         NOT NULL COMMENT '购买用户id',
    `count`          int            NOT NULL DEFAULT 1 COMMENT '购买数量',
    `properties`     json           NULL COMMENT '商品属性',
    `amount`         decimal(10, 2) NOT NULL COMMENT '订单金额',
    `original_amount` decimal(10, 2) NULL COMMENT '原始金额',
    `discount_amount` decimal(10, 2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `coupon_code`    varchar(64)    NULL COMMENT '优惠券编码',
    `outer_no`       varchar(64)    NULL COMMENT '外部订单号',
    `payment_provider` varchar(32)  NULL COMMENT '支付提供方',
    `transaction_code` varchar(64)  NULL COMMENT '支付流水号',
    `paid_time`      datetime       NULL COMMENT '支付时间',
    `expire_time`    datetime       NULL COMMENT '过期时间',
    `status`         varchar(14)    NOT NULL COMMENT '订单状态',
    `delivery_status` varchar(16)   NOT NULL DEFAULT 'WAITING' COMMENT '交付状态',
    `delivery_message` varchar(512) NULL COMMENT '交付信息',
    `create_by`      varchar(32)    NOT NULL COMMENT '创建人',
    `update_by`      varchar(32)    NOT NULL COMMENT '修改人',
    `create_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`      boolean        NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`order_no`)
);

ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `shop_id` bigint NOT NULL DEFAULT 1;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `buyer_id` bigint NOT NULL DEFAULT 0;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `count` int NOT NULL DEFAULT 1;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `properties` json NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `original_amount` decimal(10, 2) NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `discount_amount` decimal(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `coupon_code` varchar(64) NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `outer_no` varchar(64) NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `payment_provider` varchar(32) NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `transaction_code` varchar(64) NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `paid_time` datetime NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `expire_time` datetime NULL;
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `delivery_status` varchar(16) NOT NULL DEFAULT 'WAITING';
ALTER TABLE `shop_order`
    ADD COLUMN IF NOT EXISTS `delivery_message` varchar(512) NULL;
UPDATE `shop_order` SET `original_amount` = `amount` WHERE `original_amount` IS NULL;

CREATE TABLE IF NOT EXISTS `shop_order_delivery`
(
    `id`             bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `order_no`       varchar(64)  NOT NULL COMMENT '订单号',
    `item_id`        bigint       NOT NULL COMMENT '商品id',
    `buyer_id`       bigint       NOT NULL COMMENT '购买用户id',
    `delivery_type`  varchar(32)  NOT NULL COMMENT '交付类型',
    `title`          varchar(128) NULL COMMENT '提货标题',
    `content`        text         NULL COMMENT '提货内容',
    `attachments`    json         NULL COMMENT '交付附件JSON',
    `license_no`     varchar(64)  NULL COMMENT '授权编号',
    `extracted_time` datetime     NULL COMMENT '用户提取时间',
    `create_by`      varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`      varchar(32)  NOT NULL COMMENT '修改人',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`      boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`order_no`)
);

CREATE INDEX IF NOT EXISTS `idx_shop_order_delivery_buyer`
    ON `shop_order_delivery` (`buyer_id`, `create_time`);

CREATE TABLE IF NOT EXISTS `shop_license_root`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `name`        varchar(64)  NOT NULL COMMENT '根证书名称',
    `algorithm`   varchar(32)  NOT NULL COMMENT '签名算法',
    `public_key`  text         NOT NULL COMMENT '根证书公钥',
    `private_key` text         NOT NULL COMMENT '根证书私钥',
    `create_by`   varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)  NOT NULL COMMENT '修改人',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`name`)
);

CREATE TABLE IF NOT EXISTS `shop_license_key_pair`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `license_no`  varchar(64)  NOT NULL COMMENT '授权编号',
    `order_no`    varchar(64)  NOT NULL COMMENT '订单号',
    `item_id`     bigint       NOT NULL COMMENT '商品id',
    `buyer_id`    bigint       NOT NULL COMMENT '购买用户id',
    `root_id`     bigint       NOT NULL COMMENT '根证书id',
    `algorithm`   varchar(32)  NOT NULL COMMENT '密钥算法',
    `public_key`  text         NOT NULL COMMENT '授权公钥',
    `private_key` text         NOT NULL COMMENT '授权私钥',
    `certificate` text         NOT NULL COMMENT '授权证书JSON',
    `signature`   text         NOT NULL COMMENT '根证书签名',
    `create_by`   varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)  NOT NULL COMMENT '修改人',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`license_no`),
    UNIQUE (`order_no`)
);

CREATE TABLE IF NOT EXISTS `shop_coupon`
(
    `id`               bigint         NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `code`             varchar(64)    NOT NULL COMMENT '优惠券编码',
    `name`             varchar(64)    NOT NULL COMMENT '优惠券名称',
    `type`             varchar(16)    NOT NULL COMMENT '优惠类型',
    `discount_value`   decimal(10, 2) NOT NULL COMMENT '优惠值',
    `threshold_amount` decimal(10, 2) NOT NULL DEFAULT 0 COMMENT '使用门槛',
    `total_count`      int            NULL COMMENT '发放总量，空或0代表不限量',
    `used_count`       int            NOT NULL DEFAULT 0 COMMENT '已使用数量',
    `enabled`          boolean        NOT NULL DEFAULT true COMMENT '启用状态',
    `start_time`       datetime       NULL COMMENT '开始时间',
    `end_time`         datetime       NULL COMMENT '结束时间',
    `create_by`        varchar(32)    NOT NULL COMMENT '创建人',
    `update_by`        varchar(32)    NOT NULL COMMENT '修改人',
    `create_time`      datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`        boolean        NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`code`)
);

CREATE INDEX IF NOT EXISTS `idx_shop_coupon_enabled_time`
    ON `shop_coupon` (`enabled`, `start_time`, `end_time`);

CREATE TABLE IF NOT EXISTS `shop_transaction`
(
    `id`          bigint         NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `code`        varchar(64)    NOT NULL COMMENT '交易流水号',
    `order_no`    varchar(64)    NOT NULL COMMENT '关联订单号',
    `shop_id`     bigint         NOT NULL COMMENT '所属店铺id',
    `content`     varchar(512)   NOT NULL COMMENT '交易内容',
    `payer`       varchar(256)   NOT NULL COMMENT '付款人信息',
    `receiver`    varchar(256)   NOT NULL COMMENT '收款人信息',
    `amount`      decimal(10, 2) NOT NULL COMMENT '交易金额',
    `type`        varchar(10)    NOT NULL COMMENT '交易类型',
    `create_by`   varchar(32)    NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)    NOT NULL COMMENT '修改人',
    `create_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean        NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`code`)
);

CREATE TABLE IF NOT EXISTS `support_ticket`
(
    `id`            bigint        NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `ticket_no`     varchar(64)   NOT NULL COMMENT '工单号',
    `creator_id`    bigint        NOT NULL COMMENT '提交用户id',
    `title`         varchar(120)  NOT NULL COMMENT '工单标题',
    `category`      varchar(40)   NOT NULL DEFAULT 'GENERAL' COMMENT '工单类型',
    `priority`      varchar(12)   NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `status`        varchar(16)   NOT NULL DEFAULT 'OPEN' COMMENT '工单状态',
    `contact`       varchar(512)  NULL COMMENT '联系方式',
    `last_message`  varchar(1024) NULL COMMENT '最后一条消息',
    `admin_unread_count` int      NOT NULL DEFAULT 0 COMMENT '管理员未读数',
    `user_unread_count`  int      NOT NULL DEFAULT 0 COMMENT '用户未读数',
    `assignee_id`   bigint        NULL COMMENT '处理人id',
    `resolved_by`   bigint        NULL COMMENT '解决人id',
    `resolved_time` datetime      NULL COMMENT '解决时间',
    `create_by`     varchar(32)   NOT NULL COMMENT '创建人',
    `update_by`     varchar(32)   NOT NULL COMMENT '修改人',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`     boolean       NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`ticket_no`)
);

ALTER TABLE `support_ticket`
    ADD COLUMN IF NOT EXISTS `admin_unread_count` int NOT NULL DEFAULT 0;
ALTER TABLE `support_ticket`
    ADD COLUMN IF NOT EXISTS `user_unread_count` int NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS `idx_support_ticket_creator_status`
    ON `support_ticket` (`creator_id`, `status`, `update_time`);

CREATE INDEX IF NOT EXISTS `idx_support_ticket_creator_unread`
    ON `support_ticket` (`creator_id`, `user_unread_count`, `update_time`);

CREATE INDEX IF NOT EXISTS `idx_support_ticket_admin_unread`
    ON `support_ticket` (`admin_unread_count`, `update_time`);

CREATE TABLE IF NOT EXISTS `support_ticket_message`
(
    `id`          bigint        NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `ticket_id`   bigint        NOT NULL COMMENT '工单id',
    `sender_id`   bigint        NOT NULL COMMENT '发送人id',
    `sender_role` varchar(12)   NOT NULL COMMENT '发送角色',
    `content`     varchar(4096) NOT NULL COMMENT '消息内容',
    `attachments`  json          NULL COMMENT '消息附件JSON',
    `create_by`   varchar(32)   NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)   NOT NULL COMMENT '修改人',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean       NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`)
);

ALTER TABLE `support_ticket_message`
    ADD COLUMN IF NOT EXISTS `attachments` json NULL;

CREATE INDEX IF NOT EXISTS `idx_support_ticket_message_ticket`
    ON `support_ticket_message` (`ticket_id`, `create_time`);

CREATE TABLE IF NOT EXISTS `customer_conversation`
(
    `id`                 bigint        NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `user_id`            bigint        NULL COMMENT '绑定门户用户id',
    `wechat_openid`      varchar(128)  NOT NULL COMMENT '微信openid',
    `wechat_union_id`    varchar(128)  NULL COMMENT '微信unionid',
    `display_name`       varchar(128)  NULL COMMENT '客户展示名',
    `avatar`             varchar(512)  NULL COMMENT '客户头像',
    `status`             varchar(16)   NOT NULL DEFAULT 'OPEN' COMMENT '会话状态',
    `last_message`       varchar(1024) NULL COMMENT '最后一条消息',
    `last_message_time`  datetime      NULL COMMENT '最后消息时间',
    `last_inbound_time`  datetime      NULL COMMENT '最后客户来信时间',
    `admin_unread_count` int           NOT NULL DEFAULT 0 COMMENT '管理员未读数',
    `user_unread_count`  int           NOT NULL DEFAULT 0 COMMENT '用户未读数',
    `create_by`          varchar(32)   NOT NULL COMMENT '创建人',
    `update_by`          varchar(32)   NOT NULL COMMENT '修改人',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`          boolean       NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`wechat_openid`)
);

CREATE INDEX IF NOT EXISTS `idx_customer_conversation_user`
    ON `customer_conversation` (`user_id`, `update_time`);

CREATE INDEX IF NOT EXISTS `idx_customer_conversation_admin_unread`
    ON `customer_conversation` (`admin_unread_count`, `last_message_time`);

CREATE TABLE IF NOT EXISTS `customer_message`
(
    `id`              bigint        NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `conversation_id` bigint        NOT NULL COMMENT '客服会话id',
    `user_id`         bigint        NULL COMMENT '绑定门户用户id',
    `wechat_openid`   varchar(128)  NOT NULL COMMENT '微信openid',
    `direction`       varchar(12)   NOT NULL COMMENT '消息方向',
    `channel`         varchar(16)   NOT NULL COMMENT '消息渠道',
    `sender_role`     varchar(12)   NOT NULL COMMENT '发送角色',
    `sender_id`       bigint        NULL COMMENT '发送人id',
    `message_type`    varchar(24)   NOT NULL DEFAULT 'text' COMMENT '消息类型',
    `content`         varchar(4096) NOT NULL COMMENT '消息内容',
    `attachments`     json          NULL COMMENT '消息附件JSON',
    `raw_payload`     text          NULL COMMENT '原始报文',
    `wechat_msg_id`   varchar(128)  NULL COMMENT '微信消息id',
    `related_type`    varchar(32)   NULL COMMENT '关联业务类型',
    `related_no`      varchar(128)  NULL COMMENT '关联业务编号',
    `send_status`     varchar(16)   NOT NULL DEFAULT 'RECEIVED' COMMENT '发送状态',
    `error_message`   varchar(512)  NULL COMMENT '错误信息',
    `read_by_admin`   boolean       NOT NULL DEFAULT false COMMENT '管理员已读',
    `read_by_user`    boolean       NOT NULL DEFAULT false COMMENT '用户已读',
    `create_by`       varchar(32)   NOT NULL COMMENT '创建人',
    `update_by`       varchar(32)   NOT NULL COMMENT '修改人',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`       boolean       NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`)
);

ALTER TABLE `customer_message`
    ADD COLUMN IF NOT EXISTS `attachments` json NULL;

CREATE INDEX IF NOT EXISTS `idx_customer_message_conversation`
    ON `customer_message` (`conversation_id`, `create_time`);
