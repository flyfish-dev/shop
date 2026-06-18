package group.flyfish.dev.portal.api;

import java.util.Map;

public record PortalWorkbenchSummary(String capability, Map<String, Long> metrics) {

    public PortalWorkbenchSummary {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    public static PortalWorkbenchSummary empty(String capability) {
        return new PortalWorkbenchSummary(capability, Map.of());
    }

    public long metric(String name) {
        return metrics.getOrDefault(name, 0L);
    }
}
