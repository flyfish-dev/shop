package group.flyfish.dev.shop.wechat.client;

import lombok.Data;

/**
 * 只返回 errcode/errmsg 的微信 API 通用响应。
 */
@Data
public class WechatSimpleApiResponse implements WechatApiResponse {

    private Integer errcode;

    private String errmsg;
}
