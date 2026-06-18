package group.flyfish.dev.portal.api;

import reactor.core.publisher.Mono;

import java.util.List;

public interface PortalWorkbenchSummaryProvider {

    String capability();

    default String actionName() {
        return null;
    }

    default String actionPath() {
        return null;
    }

    default String actionStatus() {
        return "ready";
    }

    default List<PortalWorkbenchAction> actions() {
        if (actionName() == null || actionName().isBlank()
                || actionPath() == null || actionPath().isBlank()) {
            return List.of();
        }
        return List.of(new PortalWorkbenchAction(actionName(), actionPath(), actionStatus()));
    }

    default String metricLabel(String name) {
        return name;
    }

    Mono<PortalWorkbenchSummary> getSummary(Long userId);
}
