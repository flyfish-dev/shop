package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopOrderPaymentVo {

    private String provider;

    private String tradeType;

    private String payType;

    private String tradeNo;

    private String jumpUrl;

    private String qrcodeText;

    private LocalDateTime expireTime;
}
