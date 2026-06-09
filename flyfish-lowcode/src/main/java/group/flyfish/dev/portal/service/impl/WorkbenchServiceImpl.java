package group.flyfish.dev.portal.service.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.manager.DbConnectionManager;
import group.flyfish.dev.generator.management.repository.DbSourceRepository;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import group.flyfish.dev.portal.api.PortalWorkbenchAction;
import group.flyfish.dev.portal.api.PortalWorkbenchSummary;
import group.flyfish.dev.portal.api.PortalWorkbenchSummaryProvider;
import group.flyfish.dev.portal.domain.vo.WorkbenchVo;
import group.flyfish.dev.portal.service.WorkbenchService;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkbenchServiceImpl implements WorkbenchService {

    private final DbSourceRepository dbSourceRepository;
    private final List<PortalWorkbenchSummaryProvider> workbenchSummaryProviders;

    @Override
    public Mono<WorkbenchVo> getWorkbench(PortalUserVo user) {
        Mono<Long> sourceCount = dbSourceRepository.count();
        Mono<List<DbSource>> recentSources = dbSourceRepository.findAll()
                .take(5)
                .map(R2dbcUrlUtils::materialize)
                .map(source -> source.setPassword(DbConnectionManager.STARRED_PASSWORD))
                .collectList();
        Mono<List<PortalWorkbenchSummary>> extensionSummaries = getExtensionSummaries(user);
        return Mono.zip(sourceCount, recentSources, extensionSummaries)
                .map(tuple -> build(user, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private WorkbenchVo build(PortalUserVo user, long sourceCount, List<DbSource> recentSources,
                              List<PortalWorkbenchSummary> extensionSummaries) {
        WorkbenchVo vo = new WorkbenchVo();
        vo.setUser(user);
        WorkbenchVo.DataSourceSummary dataSources = new WorkbenchVo.DataSourceSummary();
        dataSources.setTotal(sourceCount);
        dataSources.setRecent(recentSources);
        vo.setDataSources(dataSources);
        extensionSummaries.forEach(summary -> vo.putExtensionSummary(summary.capability(), summary.metrics()));
        vo.setExtensionMetrics(extensionMetrics(extensionSummaries));
        List<WorkbenchVo.ActionItem> actions = new ArrayList<>(List.of(
                action("数据建模", "/model-design", "ready"),
                action("代码生成", "/code-generate", "ready"),
                action("在线运行", "/online-launch", "later"),
                action("集成测试", "/integrate-test", "later")
        ));
        int extensionActionIndex = 2;
        for (PortalWorkbenchSummaryProvider provider : extensionProviders()) {
            for (PortalWorkbenchAction action : provider.actions()) {
                if (!hasAction(action)) {
                    continue;
                }
                actions.add(extensionActionIndex++,
                        action(action.name(), action.path(), action.status()));
            }
        }
        vo.setActions(actions);
        return vo;
    }

    private List<WorkbenchVo.MetricItem> extensionMetrics(List<PortalWorkbenchSummary> extensionSummaries) {
        Map<String, PortalWorkbenchSummaryProvider> providers = extensionProviders().stream()
                .collect(Collectors.toMap(PortalWorkbenchSummaryProvider::capability, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        return extensionSummaries.stream()
                .flatMap(summary -> summary.metrics().entrySet().stream()
                        .map(metric -> metric(summary.capability(), metric.getKey(),
                                metricLabel(providers.get(summary.capability()), metric.getKey()),
                                metric.getValue())))
                .toList();
    }

    private Mono<List<PortalWorkbenchSummary>> getExtensionSummaries(PortalUserVo user) {
        List<PortalWorkbenchSummaryProvider> providers = extensionProviders();
        if (providers.isEmpty()) {
            return Mono.just(List.of());
        }
        Long userId = user == null ? null : user.getId();
        return Flux.fromIterable(providers)
                .flatMapSequential(provider -> provider.getSummary(userId)
                        .defaultIfEmpty(PortalWorkbenchSummary.empty(provider.capability())))
                .collectList();
    }

    private List<PortalWorkbenchSummaryProvider> extensionProviders() {
        if (workbenchSummaryProviders == null || workbenchSummaryProviders.isEmpty()) {
            return List.of();
        }
        return workbenchSummaryProviders.stream()
                .filter(provider -> hasText(provider.capability()))
                .toList();
    }

    private boolean hasAction(PortalWorkbenchAction action) {
        return action != null && hasText(action.name()) && hasText(action.path());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String metricLabel(PortalWorkbenchSummaryProvider provider, String name) {
        if (provider == null) {
            return name;
        }
        String label = provider.metricLabel(name);
        return hasText(label) ? label : name;
    }

    private WorkbenchVo.ActionItem action(String name, String path, String status) {
        WorkbenchVo.ActionItem item = new WorkbenchVo.ActionItem();
        item.setName(name);
        item.setPath(path);
        item.setStatus(status);
        return item;
    }

    private WorkbenchVo.MetricItem metric(String capability, String name, String label, long value) {
        WorkbenchVo.MetricItem item = new WorkbenchVo.MetricItem();
        item.setCapability(capability);
        item.setName(name);
        item.setLabel(label);
        item.setValue(value);
        return item;
    }
}
