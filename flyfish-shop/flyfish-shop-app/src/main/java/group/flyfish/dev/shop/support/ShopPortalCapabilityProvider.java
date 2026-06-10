package group.flyfish.dev.shop.support;

import group.flyfish.dev.portal.api.PortalCapability;
import group.flyfish.dev.portal.api.PortalCapabilityProvider;
import org.springframework.stereotype.Component;

@Component
public class ShopPortalCapabilityProvider implements PortalCapabilityProvider {

    @Override
    public PortalCapability capability() {
        return new PortalCapability("shop", "飞鱼小铺", "/shop/item-list", "ready");
    }
}
