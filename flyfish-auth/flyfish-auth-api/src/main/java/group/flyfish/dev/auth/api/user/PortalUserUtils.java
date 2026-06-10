package group.flyfish.dev.auth.api.user;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

public final class PortalUserUtils {

    private PortalUserUtils() {
    }

    public static String getAvatar(List<PortalUserOauthVo> authorizations) {
        if (authorizations == null || authorizations.isEmpty()) {
            return null;
        }
        return authorizations.stream()
                .filter(Objects::nonNull)
                .filter(vo -> vo.getType() == OAuthType.GITEA || vo.getType() == OAuthType.GITEE
                        || vo.getType() == OAuthType.GITHUB || vo.getType() == OAuthType.WECHAT)
                .map(PortalUserOauthVo::getAvatar)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }
}
