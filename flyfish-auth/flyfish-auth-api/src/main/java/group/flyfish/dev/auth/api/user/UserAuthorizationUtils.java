package group.flyfish.dev.auth.api.user;

import group.flyfish.dev.common.exception.BusinessException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
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
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITEA);
        if (oauth == null) {
            return false;
        }
        if (MAINTAINER_GITEA_ID.equals(oauth.getOpenid())) {
            return true;
        }
        Map<String, Object> gitea = getGiteaProfile(user);
        return gitea != null && Boolean.TRUE.equals(booleanValue(gitea.get("is_admin")));
    }

    public static boolean isMaintainer(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITEA);
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
        return profileOf(oauth);
    }

    public static Map<String, Object> getGithubProfile(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITHUB);
        return profileOf(oauth);
    }

    public static Map<String, Object> getGiteeProfile(PortalUserVo user) {
        PortalUserOauthVo oauth = getAuthorization(user, OAuthType.GITEE);
        return profileOf(oauth);
    }

    public static PortalUserOauthVo getAuthorization(PortalUserVo user, OAuthType type) {
        if (user == null || MapUtils.isEmpty(user.getAuthorizations())) {
            return null;
        }
        return user.getAuthorizations().get(type.getCode());
    }

    public static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> profileOf(PortalUserOauthVo oauth) {
        if (oauth == null) {
            return null;
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        if (oauth.getUserInfo() != null) {
            profile.putAll(oauth.getUserInfo());
        }
        putIfNotBlank(profile, "id", oauth.getOpenid());
        putIfNotBlank(profile, "openid", oauth.getOpenid());
        putIfNotBlank(profile, "login", oauth.getLogin());
        putIfNotBlank(profile, "username", oauth.getLogin());
        putIfNotBlank(profile, "login_name", oauth.getLogin());
        putIfNotBlank(profile, "display_name", oauth.getDisplayName());
        putIfNotBlank(profile, "name", oauth.getDisplayName());
        putIfNotBlank(profile, "nickname", oauth.getNickname());
        putIfNotBlank(profile, "avatar_url", oauth.getAvatar());
        putIfNotBlank(profile, "email", oauth.getEmail());
        putIfNotBlank(profile, "profile_url", oauth.getProfileUrl());
        putIfNotBlank(profile, "html_url", oauth.getProfileUrl());
        putIfNotBlank(profile, "union_id", oauth.getUnionId());
        return profile;
    }

    private static void putIfNotBlank(Map<String, Object> profile, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            profile.put(key, value);
        }
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
