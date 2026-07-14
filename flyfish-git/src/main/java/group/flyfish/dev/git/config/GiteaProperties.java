package group.flyfish.dev.git.config;

import lombok.Data;

@Data
public class GiteaProperties {

    private String server = "https://git.flyfish.dev";

    private String adminToken;
}
