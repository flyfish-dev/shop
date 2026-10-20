package group.flyfish.dev.xianyu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties("flyfish.xianyu")
public class XianyuProperties {
    private boolean towerDeployment;
    private boolean dataFlowApproved;
    private boolean salesEnabled;
    private boolean processingEnabled;
    private boolean miniappEnabled;
    private String gateway = "https://eco.taobao.com/router/rest";
    private String encryptionKey;
    private String bridgeSecret;
    private String shopBaseUrl = "http://127.0.0.1:10082";
    private String authBaseUrl = "http://127.0.0.1:10080";
    private String genericItemId;
    private String businessCode;
    private String virtualItemCode;
    private String miniappSeller;
    private String nativeSeller;
    private String miniappEntryUrl;
    private Map<String, Application> applications = new LinkedHashMap<>();
    private java.util.List<String> allowedOrigins = java.util.List.of();
    @Data
    public static class Application {
        private String appKey;
        private String appSecret;
    }
    public void requireInfrastructure() {
        if (!towerDeployment || !dataFlowApproved) {
            throw new IllegalStateException("Tower deployment and approved data flow are required");
        }
    }
    public void requireSales() {
        requireInfrastructure();
        if (!salesEnabled || !processingEnabled) throw new IllegalStateException("New channel sales or processing are disabled");
    }
}
