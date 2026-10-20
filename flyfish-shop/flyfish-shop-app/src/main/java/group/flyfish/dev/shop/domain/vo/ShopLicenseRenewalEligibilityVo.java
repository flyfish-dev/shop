package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 当前用户的授权续费资格。
 */
@Data
public class ShopLicenseRenewalEligibilityVo {

    @Property("是否为续费SKU")
    private boolean renewalSku;

    @Property("是否具备续费资格")
    private boolean eligible;

    @Property("不可续费原因编码")
    private String reasonCode;

    @Property("不可续费原因")
    private String message;

    @Property("单年续费价格")
    private BigDecimal annualPrice;

    @Property("单笔最大续费年数")
    private Integer maxYears;

    @Property("默认许可证号")
    private String defaultLicenseNo;

    @Property("默认生效日期")
    private LocalDate defaultStartDate;

    @Property("可续费许可证")
    private List<LicenseOption> licenses = List.of();

    /**
     * 可续费的已有许可证。
     */
    @Data
    public static class LicenseOption {

        @Property("许可证号")
        private String licenseNo;

        @Property("来源订单号")
        private String orderNo;

        @Property("授权名称")
        private String licenseName;

        @Property("授权版本")
        private String edition;

        @Property("授权版本名称")
        private String editionName;

        @Property("当前许可证是否永久有效")
        private boolean permanent;

        @Property("当前许可到期日期")
        private LocalDate expiresOn;

        @Property("建议续费生效日期")
        private LocalDate suggestedStartDate;
    }
}
