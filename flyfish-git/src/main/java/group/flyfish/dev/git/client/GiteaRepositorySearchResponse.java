package group.flyfish.dev.git.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GiteaRepositorySearchResponse(List<GitRepositoryApiRepo> data) {
}
