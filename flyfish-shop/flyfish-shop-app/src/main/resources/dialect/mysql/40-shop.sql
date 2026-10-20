ALTER TABLE `shop` COMMENT '店铺表';
ALTER TABLE `shop_item_group` COMMENT '店铺商品组表';
ALTER TABLE `shop_item` COMMENT '店铺商品表';
ALTER TABLE `shop_item_sku` COMMENT '商品 SKU 表';
ALTER TABLE `shop_item_sku_contract` COMMENT '商品 SKU 合同绑定表';
ALTER TABLE `shop_item_view_stat` COMMENT '商品查看统计表';
ALTER TABLE `shop_contract` COMMENT '小铺合同表';
ALTER TABLE `shop_contract_file` COMMENT '小铺合同文件表';
ALTER TABLE `shop_item_contract` COMMENT '商品合同绑定表';
ALTER TABLE `shop_contract_signature` COMMENT '合同签署留痕表';
ALTER TABLE `shop_license_issue_history` COMMENT '授权许可证签发历史表';
ALTER TABLE `shop_order` COMMENT '店铺订单表';
ALTER TABLE `shop_transaction` COMMENT '店铺交易流水表';
ALTER TABLE `shop_order` MODIFY COLUMN `transaction_code` varchar(128) NULL COMMENT '支付流水号';
ALTER TABLE `shop_transaction` MODIFY COLUMN `code` varchar(128) NOT NULL COMMENT '交易流水号';

-- 组合商品会在同一订单内分别签发多套产品授权，组件标识保证单品兼容与组合交付幂等。
-- IF EXISTS/IF NOT EXISTS 保证全新库与已迁移生产库都可以重复执行初始化脚本。
DROP INDEX IF EXISTS `order_no` ON `shop_order_delivery`;
DROP INDEX IF EXISTS `uk_shop_order_delivery_order_type` ON `shop_order_delivery`;
CREATE UNIQUE INDEX IF NOT EXISTS `uk_shop_order_delivery_order_type`
    ON `shop_order_delivery` (`order_no`, `delivery_type`, `component_key`);
DROP INDEX IF EXISTS `order_no` ON `shop_license_key_pair`;
CREATE UNIQUE INDEX IF NOT EXISTS `uk_shop_license_order_component`
    ON `shop_license_key_pair` (`order_no`, `component_key`);
