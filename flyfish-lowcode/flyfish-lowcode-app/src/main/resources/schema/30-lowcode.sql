CREATE TABLE IF NOT EXISTS `db_source`
(
    `id`          bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `source_key`  varchar(64)  NOT NULL COMMENT '数据源key',
    `key`         varchar(64)  NULL COMMENT '旧版数据源key',
    `name`        varchar(128) NOT NULL COMMENT '数据源名称',
    `url`         varchar(512) NOT NULL COMMENT '派生数据源url',
    `type`        varchar(32)  NOT NULL DEFAULT 'mysql' COMMENT '数据库类型',
    `host`        varchar(255) NULL COMMENT '主机地址',
    `port`        int          NULL COMMENT '端口',
    `database_name` varchar(128) NULL COMMENT '数据库名',
    `params`      varchar(512) NULL COMMENT '连接参数',
    `username`    varchar(64)  NOT NULL COMMENT '用户名',
    `password`    varchar(128) NOT NULL COMMENT '密码',
    `owner`       varchar(32)  NOT NULL COMMENT '所有者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`)
);

ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `source_key` varchar(64) NULL;
UPDATE `db_source` SET `source_key` = `key` WHERE `source_key` IS NULL AND `key` IS NOT NULL;
ALTER TABLE `db_source`
    MODIFY COLUMN `key` varchar(64) NULL;
ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `type` varchar(32) NOT NULL DEFAULT 'mysql';
ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `host` varchar(255) NULL;
ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `port` int NULL;
ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `database_name` varchar(128) NULL;
ALTER TABLE `db_source`
    ADD COLUMN IF NOT EXISTS `params` varchar(512) NULL;
