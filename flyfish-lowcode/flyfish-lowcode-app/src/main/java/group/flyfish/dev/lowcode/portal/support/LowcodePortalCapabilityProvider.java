package group.flyfish.dev.lowcode.portal.support;

import group.flyfish.dev.portal.api.PortalCapability;
import group.flyfish.dev.portal.api.PortalCapabilityProvider;
import org.springframework.stereotype.Component;

@Component
public class LowcodePortalCapabilityProvider implements PortalCapabilityProvider {

    @Override
    public PortalCapability capability() {
        return new PortalCapability("lowcode", "飞鱼低代码平台", "/model-design", "ready");
    }
}
