package group.flyfish.dev.user.domain.vo;

import group.flyfish.dev.user.domain.po.PortalUser;
import group.flyfish.dev.user.utils.PortalUserUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * 门户用户详情
 *
 * @author wangyu
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

    public PortalUserVo(PortalUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.avatar = user.getAvatar();
        this.phone = user.getPhone();
        this.email = user.getEmail();
        this.bio = user.getBio();
        this.createTime = user.getCreateTime();
        this.updateTime = user.getUpdateTime();
    }

    public PortalUserVo(PortalUser user, List<PortalUserOauthVo> authorizations) {
        this(user);
        List<PortalUserOauthVo> safeAuthorizations = authorizations == null ? List.of() : authorizations;
        this.authorizations = safeAuthorizations.stream()
                .filter(Objects::nonNull)
                .filter(vo -> vo.getType() != null)
                .collect(Collectors.toMap(vo -> vo.getType().getCode(), vo -> vo, (left, right) -> right));
        this.avatar = StringUtils.defaultIfBlank(this.avatar, PortalUserUtils.getAvatar(safeAuthorizations));
    }
}
