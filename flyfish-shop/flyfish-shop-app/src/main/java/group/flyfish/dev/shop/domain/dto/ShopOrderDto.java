package group.flyfish.dev.shop.domain.dto;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 下单数据
 * @author wangyu
 */
@Data
public class ShopOrderDto {

    @Property("商品id")
    private String itemId;

    @Property("SKU id")
    private String skuId;

    @Property("商品属性")
    private Map<String, Object> properties;

    @Property("商品数量")
    private Integer count;

    @Property("优惠券编码")
    private String couponCode;

    @Property("打赏金额")
    private BigDecimal donationAmount;

    @Property(value = "支付币种", description = "纯打赏支持CNY/USD")
    private String paymentCurrency;

    @Property(value = "支付提供方", description = "支持 h5zhifu/stripe")
    private String paymentProvider;

    @Property(value = "支付类型", description = "支持wechat")
    private String payType = "wechat";

    @Property(value = "支付场景", description = "支持native/h5/jsapi")
    private String tradeType;

    @Property("支付页面语言")
    private String paymentLocale;

    @Property("支付完成返回路径")
    private String paymentReturnPath;

    @Property("合同签署令牌")
    private String contractSignToken;
}
