package group.flyfish.dev.git.config;

import lombok.Data;

@Data
public class GithubProperties {

    private String apiBaseUrl = "https://api.github.com";

    private String adminToken;
}
