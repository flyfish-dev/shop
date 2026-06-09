package group.flyfish.dev.git.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitRepositoryApiRepo(
        String name,
        String description,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("clone_url") String cloneUrl,
        @JsonProperty("ssh_url") String sshUrl,
        String path,
        Boolean privateRepo,
        @JsonProperty("private") Boolean privateValue,
        @JsonProperty("_private") Boolean underscorePrivateValue,
        GitRepositoryOwner owner,
        GitRepositoryNamespace namespace,
        Map<String, Boolean> permissions
) {

    public boolean isPrivate() {
        return Boolean.TRUE.equals(privateRepo)
                || Boolean.TRUE.equals(privateValue)
                || Boolean.TRUE.equals(underscorePrivateValue);
    }

    public String ownerName() {
        return StringUtils.firstNonBlank(
                owner == null ? null : owner.login(),
                owner == null ? null : owner.username(),
                owner == null ? null : owner.name(),
                namespace == null ? null : namespace.path(),
                namespace == null ? null : namespace.name(),
                StringUtils.substringBefore(fullName, "/"));
    }

    public String repoName() {
        return StringUtils.firstNonBlank(path, name, StringUtils.substringAfter(fullName, "/"));
    }

    public String normalizedFullName() {
        String ownerName = ownerName();
        String repoName = repoName();
        if (StringUtils.isNoneBlank(ownerName, repoName)) {
            return ownerName + "/" + repoName;
        }
        return fullName;
    }

    public String url() {
        return StringUtils.firstNonBlank(htmlUrl, cloneUrl, sshUrl);
    }

    public String permission() {
        if (hasPermission("admin")) {
            return "admin";
        }
        if (hasPermission("push") || hasPermission("write")) {
            return "write";
        }
        return "read";
    }

    private boolean hasPermission(String key) {
        return permissions != null && Boolean.TRUE.equals(permissions.get(key));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitRepositoryOwner(String login, String username, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitRepositoryNamespace(String path, String name) {
    }
}
