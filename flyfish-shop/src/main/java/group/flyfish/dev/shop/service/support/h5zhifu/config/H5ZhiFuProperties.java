package group.flyfish.dev.shop.service.support.h5zhifu.config;

import lombok.Data;

@Data
public class H5ZhiFuProperties {

    /**
     * H5支付接口网关地址。官方文档默认是 https://open.h5zhifu.com/api；
     * 平台后台也可能提供北京、上海、广州、成都等区域网关，配置时允许写到域名或 /api 路径。
     */
    private String apiBaseUrl = "https://open.h5zhifu.com/api";

    /**
     * 平台后台分配的应用 ID，参与所有下单和回调验签。
     */
    private Long appId;

    /**
     * 平台后台分配的通信密钥。只用于服务端签名/验签，不能下发到前端。
     */
    private String key;

    /**
     * 支付成功异步通知地址。该地址需要公网 HTTPS 可访问，回调处理成功时必须返回纯文本 success。
     */
    private String notifyUrl;

    /**
     * 默认支付渠道。扫码/H5 支持 wechat、alipay；JSAPI 会在服务层强制改为 wechat。
     */
    private String defaultPayType = "wechat";

    /**
     * 默认支付场景。PC 使用 native，手机浏览器使用 h5，微信内置浏览器可使用 jsapi。
     */
    private String defaultTradeType = "native";

    /**
     * H5支付文档提示部分防火墙会检查 User-Agent，因此服务端请求固定携带常见浏览器 UA。
     */
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    public String getNormalizedApiBaseUrl() {
        String url = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        if (url.isEmpty()) {
            url = "https://open.h5zhifu.com/api";
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.endsWith("/api") ? url : url + "/api";
    }
}
