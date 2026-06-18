package group.flyfish.dev.git.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Git 平台仓库协作者信息。
 *
 * <p>GitHub 与 Gitea 的用户字段名称并不完全一致，这里只保留业务需要的
 * 账号标识与权限字段，并忽略其它响应字段，便于两个平台复用同一套判断逻辑。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitRepositoryCollaborator(
        Object id,
        String login,
        String username,
        @JsonProperty("login_name") String loginName,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl,
        Map<String, Boolean> permissions,
        @JsonProperty("role_name") String roleName
) {

    public String accountName() {
        return StringUtils.firstNonBlank(login, username, loginName, fullName);
    }

    public String idText() {
        return id == null ? null : String.valueOf(id);
    }
}
