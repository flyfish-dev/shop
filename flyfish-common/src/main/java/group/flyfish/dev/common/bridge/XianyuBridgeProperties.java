package group.flyfish.dev.common.bridge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("flyfish.xianyu.bridge")
public class XianyuBridgeProperties {
    private boolean enabled;
    private boolean dataFlowApproved;
    private String secret;
}
