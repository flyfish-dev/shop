CREATE TABLE IF NOT EXISTS `file_metadata`
(
    `id`                bigint       NOT NULL COMMENT '主键' AUTO_INCREMENT,
    `hash`              varchar(64)  NOT NULL COMMENT '文件哈希值',
    `original_filename` varchar(256) NOT NULL COMMENT '原始文件名',
    `path`              varchar(512) NOT NULL COMMENT '存储路径',
    `size`              bigint       NOT NULL COMMENT '文件大小',
    `content_type`      varchar(128) NOT NULL COMMENT '文件类型',
    `url`               varchar(512) NOT NULL COMMENT '访问URL',
    `create_by`         varchar(32)  NOT NULL COMMENT '创建人',
    `update_by`         varchar(32)  NOT NULL COMMENT '修改人',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `is_delete`         boolean      NOT NULL DEFAULT false COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE (`hash`)
);
