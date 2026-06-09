package group.flyfish.dev.oauth.config;

import lombok.Data;

@Data
public class Pac4jProperties {

    private final Callback callback = new Callback();

    @Data
    public static class Callback {

        private String defaultUrl;
        private Boolean renewSession;
        private String defaultClient;
    }
}
