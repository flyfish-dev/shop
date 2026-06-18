package group.flyfish.dev.platform.portal.controller;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.portal.api.PortalCapability;
import group.flyfish.dev.portal.api.PortalCapabilityProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalCapabilityController {

    private final ObjectProvider<PortalCapabilityProvider> capabilityProviders;

    @GetMapping("capabilities")
    public Result<List<PortalCapability>> getCapabilities() {
        Map<String, PortalCapability> capabilities = new LinkedHashMap<>();
        capabilityProviders.orderedStream()
                .map(PortalCapabilityProvider::capability)
                .filter(capability -> capability != null && capability.code() != null)
                .forEach(capability -> capabilities.putIfAbsent(capability.code(), capability));
        return Result.ok(List.copyOf(capabilities.values()));
    }
}
