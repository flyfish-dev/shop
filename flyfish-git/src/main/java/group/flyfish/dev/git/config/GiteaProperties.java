package group.flyfish.dev.git.config;

import lombok.Data;

@Data
public class GiteaProperties {

    private String server = "https://git.example.com";

    private String adminToken;
}
