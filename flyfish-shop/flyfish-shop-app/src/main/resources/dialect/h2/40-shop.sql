COMMENT ON TABLE `shop` IS '商铺表';
COMMENT ON TABLE `shop_item_group` IS '商品分组表';
COMMENT ON TABLE `shop_item` IS '商品表';
COMMENT ON TABLE `shop_item_view_stat` IS '商品查看统计表';
COMMENT ON TABLE `shop_contract` IS '小铺合同表';
COMMENT ON TABLE `shop_contract_file` IS '小铺合同文件表';
COMMENT ON TABLE `shop_item_contract` IS '商品合同绑定表';
COMMENT ON TABLE `shop_contract_signature` IS '合同签署留痕表';
COMMENT ON TABLE `shop_license_issue_history` IS '授权许可证签发历史表';
COMMENT ON TABLE `shop_order` IS '订单表';
COMMENT ON TABLE `shop_transaction` IS '交易流水表';
ALTER TABLE `shop_order` ALTER COLUMN `transaction_code` SET DATA TYPE varchar(128);
ALTER TABLE `shop_transaction` ALTER COLUMN `code` SET DATA TYPE varchar(128);
DROP INDEX IF EXISTS `order_no`;
DROP INDEX IF EXISTS `uk_shop_order_delivery_order_type`;
CREATE UNIQUE INDEX IF NOT EXISTS `uk_shop_order_delivery_order_type`
    ON `shop_order_delivery` (`order_no`, `delivery_type`, `component_key`);
CREATE UNIQUE INDEX IF NOT EXISTS `uk_shop_license_order_component`
    ON `shop_license_key_pair` (`order_no`, `component_key`);
