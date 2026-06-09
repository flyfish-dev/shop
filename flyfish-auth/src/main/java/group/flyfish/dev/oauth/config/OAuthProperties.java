package group.flyfish.dev.oauth.config;

import lombok.Data;

/**
 * 开放认证配置
 * @author wangyu
 */
@Data
public class OAuthProperties {

    private Gitea gitea = new Gitea();

    private Gitee gitee = new Gitee();

    private Github github = new Github();

    private String callbackUrl = "http://127.0.0.1:9999/oauth/callback";

    private static OAuthProperties INSTANCE;

    public static OAuthProperties instance() {
        return INSTANCE;
    }

    public OAuthProperties() {
        INSTANCE = this;
    }

    @Data
    public static class Gitea {

        private String appName = "飞鱼";

        private String server = "https://git.flyfish.dev";

        private String clientId = "";

        private String clientSecret = "";

        private String adminToken;
    }

    @Data
    public static class Gitee {

        private String appName = "飞鱼";

        private String server = "https://gitee.com";

        private String apiBaseUrl = "https://gitee.com/api/v5";

        private String clientId = "";

        private String clientSecret = "";

        private String scope = "user_info emails";
    }

    @Data
    public static class Github {

        private String appName = "飞鱼";

        private String server = "https://github.com";

        private String apiBaseUrl = "https://api.github.com";

        private String clientId = "";

        private String clientSecret = "";

        private String scope = "read:user user:email";
    }
}
