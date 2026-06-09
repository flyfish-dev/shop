package group.flyfish.dev.git.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Git 平台仓库协作者有效权限。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitRepositoryCollaboratorPermission(
        String permission,
        @JsonProperty("role_name") String roleName,
        GitRepositoryCollaborator user
) {
}
