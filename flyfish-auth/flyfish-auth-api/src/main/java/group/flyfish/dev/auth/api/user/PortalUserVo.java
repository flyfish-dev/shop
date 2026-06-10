package group.flyfish.dev.auth.api.user;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 门户用户详情。
 */
@Data
@NoArgsConstructor
public class PortalUserVo {

    private Long id;

    private String username;

    private String avatar;

    private String phone;

    private String email;

    private String bio;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Map<String, PortalUserOauthVo> authorizations;

    public void applyAuthorizations(List<PortalUserOauthVo> values) {
        List<PortalUserOauthVo> safeAuthorizations = values == null ? List.of() : values;
        this.authorizations = safeAuthorizations.stream()
                .filter(Objects::nonNull)
                .filter(vo -> vo.getType() != null)
                .collect(Collectors.toMap(vo -> vo.getType().getCode(), vo -> vo, (left, right) -> right));
        this.avatar = StringUtils.defaultIfBlank(this.avatar, PortalUserUtils.getAvatar(safeAuthorizations));
    }
}
