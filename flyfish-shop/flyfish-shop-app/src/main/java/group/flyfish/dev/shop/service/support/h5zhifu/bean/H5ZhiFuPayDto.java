package group.flyfish.dev.shop.service.support.h5zhifu.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * H5支付统一下单请求。
 *
 * <p>字段严格对应 H5支付官方扫码/H5/JSAPI 三个下单接口的公共入参。不要在这里添加
 * 文档未声明的业务字段；如需追踪内部信息，使用 attach 原样透传，避免平台侧签名校验或
 * 参数格式校验因为未知字段失败。</p>
 *
 * @author wangyu
 */
@Data
public class H5ZhiFuPayDto {

    @JsonProperty("app_id")
    @Property("应用ID，在后台系统设置页面查看")
    private Long appId;

    @JsonProperty("out_trade_no")
    @Property("商家订单编号，即你自己系统里的订单编号，便于后续对账")
    private String outTradeNo;

    @Property("销售商品描述")
    private String description;

    @JsonProperty("pay_type")
    @Property("支付类型，枚举值：alipay/wechat")
    private PayType payType;

    @Property("订单金额（注意单位为分，且为整数）")
    private Integer amount;

    @Property("（选填）开发者自定义数据，在支付成功后notify回调的时候会原样返回")
    private String attach;

    @JsonProperty("notify_url")
    @Property("支付成功后回调地址")
    private String notifyUrl;

    @Property("数据签名，请查看左侧签名算法")
    private String sign;

    public enum PayType {

        wechat, alipay
    }
}
