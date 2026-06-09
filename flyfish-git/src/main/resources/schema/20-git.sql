CREATE TABLE IF NOT EXISTS `git_api_token`
(
    `id`          bigint        NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `provider`    varchar(16)   NOT NULL COMMENT '代码平台',
    `name`        varchar(128)  NOT NULL COMMENT 'Token名称',
    `description` varchar(512)  NULL COMMENT '中文描述',
    `token_value` varchar(2048) NOT NULL COMMENT 'API Token',
    `username`    varchar(128)  NULL COMMENT 'Token归属账号',
    `expire_time` datetime      NULL COMMENT '过期时间',
    `enabled`     boolean       NOT NULL DEFAULT true COMMENT '启用状态',
    `sort`        int           NOT NULL DEFAULT 0 COMMENT '排序',
    `create_by`   varchar(32)   NOT NULL COMMENT '创建人',
    `update_by`   varchar(32)   NOT NULL COMMENT '修改人',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`   boolean       NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`)
);

CREATE INDEX IF NOT EXISTS `idx_git_api_token_provider_enabled`
    ON `git_api_token` (`provider`, `enabled`, `sort`);

CREATE TABLE IF NOT EXISTS `git_repository`
(
    `id`              bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `provider`        varchar(16)  NOT NULL COMMENT '代码平台',
    `access_token_id` bigint       NULL COMMENT 'API Token ID',
    `owner`           varchar(128) NOT NULL COMMENT '仓库Owner',
    `repo`            varchar(128) NOT NULL COMMENT '仓库名称',
    `full_name`       varchar(256) NOT NULL COMMENT '仓库全名',
    `name`            varchar(128) NOT NULL COMMENT '管理名称',
    `description`     varchar(512) NULL COMMENT '中文描述',
    `permission`      varchar(32)  NOT NULL DEFAULT 'read' COMMENT '交付权限',
    `url`             varchar(512) NULL COMMENT '仓库地址',
    `private_repo`    boolean      NOT NULL DEFAULT true COMMENT '是否私有仓库',
    `expire_time`     datetime     NULL COMMENT '过期时间',
    `enabled`         boolean      NOT NULL DEFAULT true COMMENT '启用状态',
    `sort`            int          NOT NULL DEFAULT 0 COMMENT '排序',
    `create_by`       varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`       varchar(32)  NOT NULL COMMENT '修改人',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`       boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`provider`, `full_name`)
);

CREATE INDEX IF NOT EXISTS `idx_git_repository_provider_enabled`
    ON `git_repository` (`provider`, `enabled`, `sort`);
