package group.flyfish.dev.shop.wechat.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WechatQrCodeTicketResponse implements WechatApiResponse {

    private String ticket;

    @JsonProperty("expire_seconds")
    private Integer expireSeconds;

    private String url;

    private Integer errcode;

    private String errmsg;
}
