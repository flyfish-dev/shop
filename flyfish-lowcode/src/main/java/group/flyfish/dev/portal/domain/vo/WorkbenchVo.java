package group.flyfish.dev.portal.domain.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class WorkbenchVo {

    private PortalUserVo user;

    private DataSourceSummary dataSources;

    @JsonIgnore
    private final Map<String, Map<String, Long>> extensionSummaries = new LinkedHashMap<>();

    private List<MetricItem> extensionMetrics;

    private List<ActionItem> actions;

    public void putExtensionSummary(String capability, Map<String, Long> metrics) {
        extensionSummaries.put(capability, metrics == null ? Map.of() : metrics);
    }

    @JsonAnyGetter
    public Map<String, Map<String, Long>> extensionSummaries() {
        return extensionSummaries;
    }

    @Data
    public static class DataSourceSummary {
        private long total;
        private List<DbSource> recent;
    }

    @Data
    public static class ActionItem {
        private String name;
        private String path;
        private String status;
    }

    @Data
    public static class MetricItem {
        private String capability;
        private String name;
        private String label;
        private long value;
    }
}
