package group.flyfish.dev.shop.service.support.h5zhifu.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * H5支付统一下单响应数据。
 *
 * <p>不同支付场景返回的支付入口不同：扫码支付返回 code_url，H5/JSAPI 返回 jump_url。
 * 服务层会根据 tradeType 做必填校验，避免前端拿到空链接后表现成“无反应”。</p>
 *
 * @author wangyu
 */
@Data
public class H5ZhiFuPayResultDto {

    @JsonProperty("trade_no")
    @Property("本平台唯一订单编号，可用于查询订单状态")
    private String tradeNo;

    @JsonProperty("code_url")
    @Property("支付二维码内容链接，开发者在前端页面可以用jquery的qrcode插件将此链接字符串渲染出二维码图片。或者百度搜索使用“将网址转为二维码的API接口”")
    private String codeUrl;

    @JsonProperty("jump_url")
    @Property("H5支付跳转地址")
    private String jumpUrl;

    @JsonProperty("expire_time")
    @Property("订单过期具体时间，目前暂时指定为2小时")
    private String expireTime;
}
