package group.flyfish.dev.user.support;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.user.domain.OAuthType;
import group.flyfish.dev.user.domain.vo.PortalUserOauthVo;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

public final class UserAuthorizationUtils {

    private static final String MAINTAINER_GITEA_ID = "1";

    private UserAuthorizationUtils() {
    }

    public static void requireLogin(PortalUserVo user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
    }

    public static void requireAdmin(PortalUserVo user) {
        requireLogin(user);
        if (!isAdmin(user)) {
            throw new BusinessException("FORBIDDEN", "需要管理员权限");
        }
    }

    public static void requireMaintainer(PortalUserVo user) {
        requireLogin(user);
        if (!isMaintainer(user)) {
            throw new BusinessException("FORBIDDEN", "无权操作");
        }
    }

    public static boolean isAdmin(PortalUserVo user) {
        Map<String, Object> gitea = getGiteaProfile(user);
        return gitea != null && Boolean.TRUE.equals(booleanValue(gitea.get("is_admin")));
    }

    public static boolean isMaintainer(PortalUserVo user) {
        PortalUserOauthVo oauth = getGiteaAuthorization(user);
        if (oauth == null) {
            return false;
        }
        if (MAINTAINER_GITEA_ID.equals(oauth.getOpenid())) {
            return true;
        }
        Map<String, Object> userInfo = oauth.getUserInfo();
        return userInfo != null && MAINTAINER_GITEA_ID.equals(stringValue(userInfo.get("id")));
    }

    public static Map<String, Object> getGiteaProfile(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITEA);
        return oauth == null ? null : oauth.getUserInfo();
    }

    public static Map<String, Object> getGithubProfile(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITHUB);
        return oauth == null ? null : oauth.getUserInfo();
    }

    public static Map<String, Object> getGiteeProfile(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITEE);
        return oauth == null ? null : oauth.getUserInfo();
    }

    private static PortalUserOauthVo getGiteaAuthorization(PortalUserVo user) {
        return getAuthorization(user, OAuthType.GITEA);
    }

    private static PortalUserOauthVo getAuthorization(PortalUserVo user, OAuthType type) {
        if (user == null || MapUtils.isEmpty(user.getAuthorizations())) {
            return null;
        }
        return user.getAuthorizations().get(type.getCode());
    }

    public static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return Boolean.FALSE;
    }
}
