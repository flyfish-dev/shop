package group.flyfish.dev.shop.license;

import group.flyfish.dev.auth.api.user.PortalUserVo;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import org.springframework.stereotype.Service;

/**
 * 开源版授权签发扩展点。
 * <p>真实商业授权签发器由私有模块提供；公开仓库只保留业务编排需要的服务边界。</p>
 */
@Service
public class ExternalLicenseIssuer {

    public IssuedLicenseDocument issue(String licenseNo, ShopOrder order, ShopItem item,
                                       PortalUserVo buyer, LicenseDeliveryParamValue param) {
        throw new ServiceException("开源版不包含商业授权签发器，请接入私有签发服务后启用 LICENSE 自动交付");
    }
}
