package group.flyfish.dev.auth.client;

import lombok.Data;

/**
 * 认证服务客户端配置。
 */
@Data
public class AuthClientProperties {

    /**
     * 认证服务内部访问地址。
     */
    private String baseUrl = "http://127.0.0.1:10080";
}
