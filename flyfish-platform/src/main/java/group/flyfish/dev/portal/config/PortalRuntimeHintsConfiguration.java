package group.flyfish.dev.portal.config;

import group.flyfish.dev.common.config.RuntimeHintsSupport;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(PortalRuntimeHintsConfiguration.PortalRuntimeHints.class)
public class PortalRuntimeHintsConfiguration {

    public static class PortalRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.portal.api.PortalCapability");
        }
    }
}
