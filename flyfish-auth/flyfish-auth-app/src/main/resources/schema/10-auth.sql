CREATE TABLE IF NOT EXISTS `user_portal`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `username`    varchar(64)  NOT NULL COMMENT '用户名',
    `password`    varchar(128) NOT NULL COMMENT '密码',
    `avatar`      varchar(256) NULL COMMENT '头像',
    `phone`       varchar(32)  NULL COMMENT '手机号',
    `email`       varchar(128) NULL COMMENT '邮箱',
    `bio`         varchar(1024) NULL COMMENT '个人简介',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE (`username`)
);

ALTER TABLE `user_portal`
    ADD COLUMN IF NOT EXISTS `avatar` varchar(256) NULL;
ALTER TABLE `user_portal`
    ADD COLUMN IF NOT EXISTS `phone` varchar(32) NULL;
ALTER TABLE `user_portal`
    ADD COLUMN IF NOT EXISTS `email` varchar(128) NULL;
ALTER TABLE `user_portal`
    ADD COLUMN IF NOT EXISTS `bio` varchar(1024) NULL;

CREATE TABLE IF NOT EXISTS `user_portal_oauth`
(
    `user_id`      bigint       NOT NULL COMMENT '用户id',
    `type`         varchar(32)  NOT NULL COMMENT '认证类型',
    `user_info`    json         NOT NULL COMMENT '第三方原始用户信息快照',
    `openid`       varchar(128) NOT NULL COMMENT '唯一开放id',
    `login_name`   varchar(128) NULL COMMENT '第三方登录名',
    `display_name` varchar(128) NULL COMMENT '第三方展示名',
    `nickname`     varchar(128) NULL COMMENT '第三方昵称',
    `avatar_url`   varchar(512) NULL COMMENT '第三方头像',
    `email`        varchar(255) NULL COMMENT '第三方邮箱',
    `profile_url`  varchar(512) NULL COMMENT '第三方主页',
    `union_id`     varchar(128) NULL COMMENT '跨应用统一id',
    `auth_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '认证时间',
    PRIMARY KEY (`user_id`, `type`, `openid`)
);

ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `login_name` varchar(128) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `display_name` varchar(128) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `nickname` varchar(128) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `avatar_url` varchar(512) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `email` varchar(255) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `profile_url` varchar(512) NULL;
ALTER TABLE `user_portal_oauth`
    ADD COLUMN IF NOT EXISTS `union_id` varchar(128) NULL;
