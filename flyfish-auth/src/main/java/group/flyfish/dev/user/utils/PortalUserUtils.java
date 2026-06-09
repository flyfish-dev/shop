package group.flyfish.dev.user.utils;

import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.vo.PortalUserOauthVo;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

public class PortalUserUtils {

    /**
     * 获取头像
     *
     * @param authorizations 认证
     * @return 结果
     */
    public static String getAvatar(List<PortalUserOauthVo> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            return null;
        }
        return authorizations.stream()
                .filter(Objects::nonNull)
                .filter(vo -> vo.getType() == OAuthType.GITEA || vo.getType() == OAuthType.GITEE
                        || vo.getType() == OAuthType.GITHUB
                        || vo.getType() == OAuthType.WECHAT)
                .map(PortalUserOauthVo::getAvatar)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
