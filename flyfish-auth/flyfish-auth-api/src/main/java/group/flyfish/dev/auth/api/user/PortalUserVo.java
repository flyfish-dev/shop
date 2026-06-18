package group.flyfish.dev.auth.api.user;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

    /**
     * 返回适合接口输出和跨服务传输的用户资料。
     *
     * <p>授权里的 {@code userInfo} 是第三方平台原始快照，体积大且可能包含敏感 token。
     * 用户管理、订单、工单和客服只需要规范化后的展示字段，因此这里统一去掉原始快照。</p>
     */
    public PortalUserVo withoutAuthorizationUserInfo() {
        PortalUserVo vo = new PortalUserVo();
        vo.id = id;
        vo.username = username;
        vo.avatar = avatar;
        vo.phone = phone;
        vo.email = email;
        vo.bio = bio;
        vo.createTime = createTime;
        vo.updateTime = updateTime;
        if (authorizations != null) {
            vo.authorizations = authorizations.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> entry.getValue().withoutUserInfo(),
                            (left, right) -> right,
                            LinkedHashMap::new));
        }
        return vo;
    }

    public void applyAuthorizations(List<PortalUserOauthVo> values) {
        List<PortalUserOauthVo> safeAuthorizations = values == null ? List.of() : values;
        this.authorizations = safeAuthorizations.stream()
                .filter(Objects::nonNull)
                .filter(vo -> vo.getType() != null)
                .collect(Collectors.toMap(vo -> vo.getType().getCode(), vo -> vo, (left, right) -> right));
        this.avatar = StringUtils.defaultIfBlank(this.avatar, PortalUserUtils.getAvatar(safeAuthorizations));
    }
}
