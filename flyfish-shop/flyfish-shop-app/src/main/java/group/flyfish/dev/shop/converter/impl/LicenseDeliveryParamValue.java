package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.shop.converter.ShopItemParamValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 授权许可交付参数。
 * <p>授权密钥由系统根证书签发，商品参数只描述授权语义，不保存密钥材料。</p>
 */
@Data
public class LicenseDeliveryParamValue implements ShopItemParamValue {

    /**
     * 授权名称，例如 Office 预览套件企业授权。
     */
    private String licenseName;

    /**
     * 授权范围，例如 product:office-viewer 或 repo:flyfish-dev/viewer。
     */
    private String scope;

    /**
     * 有效天数。为空或小于等于 0 表示长期有效。
     */
    private Integer validDays;

    /**
     * 展示给用户的授权说明。
     */
    private String remark;

    public void normalize(String fallbackName) {
        licenseName = StringUtils.defaultIfBlank(StringUtils.trimToNull(licenseName),
                StringUtils.defaultIfBlank(fallbackName, "飞鱼小铺授权许可"));
        scope = StringUtils.defaultIfBlank(StringUtils.trimToNull(scope), "product:" + licenseName);
        remark = StringUtils.trimToNull(remark);
        if (validDays != null && validDays <= 0) {
            validDays = null;
        }
    }
}
