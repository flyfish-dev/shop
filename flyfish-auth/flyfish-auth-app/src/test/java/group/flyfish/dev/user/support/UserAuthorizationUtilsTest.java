package group.flyfish.dev.user.support;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.UserAuthorizationUtils;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserAuthorizationUtilsTest {

    @Test
    void permitsOnlyGiteaAccountOneAsMaintainer() {
        PortalUserVo user = giteaUser(100L, "1", "{\"id\":\"1\",\"is_admin\":false}");

        assertTrue(UserAuthorizationUtils.isMaintainer(user));
        assertDoesNotThrow(() -> UserAuthorizationUtils.requireMaintainer(user));
    }

    @Test
    void rejectsOtherGiteaAdminFromMaintainerAccess() {
        PortalUserVo user = giteaUser(101L, "2", "{\"id\":\"2\",\"is_admin\":true}");

        assertTrue(UserAuthorizationUtils.isAdmin(user));
        assertFalse(UserAuthorizationUtils.isMaintainer(user));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserAuthorizationUtils.requireMaintainer(user));
        assertEquals("FORBIDDEN", exception.getCode());
    }

    @Test
    void rejectsAnonymousDefaultUserEvenWhenOauthPayloadLooksValid() {
        PortalUserVo user = giteaUser(0L, "1", "{\"id\":\"1\",\"is_admin\":true}");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserAuthorizationUtils.requireMaintainer(user));
        assertEquals("UNAUTHORIZED", exception.getCode());
    }

    @Test
    void readsGithubProfileFromUnifiedAuthorizations() {
        PortalUserOauth oauth = new PortalUserOauth();
        oauth.setType(OAuthType.GITHUB);
        oauth.setOpenid("1");
        oauth.setUserInfo("{\"id\":\"1\",\"login\":\"octocat\"}");

        PortalUserVo user = new PortalUserVo();
        user.setId(100L);
        user.setAuthorizations(Map.of("github", oauthVo(oauth)));

        assertEquals("octocat", UserAuthorizationUtils.getGithubProfile(user).get("login"));
    }

    private PortalUserVo giteaUser(Long userId, String openid, String userInfo) {
        PortalUserOauth oauth = new PortalUserOauth();
        oauth.setType(OAuthType.GITEA);
        oauth.setOpenid(openid);
        oauth.setUserInfo(userInfo);

        PortalUserVo user = new PortalUserVo();
        user.setId(userId);
        user.setAuthorizations(Map.of("gitea", oauthVo(oauth)));
        return user;
    }

    private static PortalUserOauthVo oauthVo(PortalUserOauth oauth) {
        return PortalUserOauthVo.of(oauth.getUserId(), oauth.getType(), oauth.getOpenid(), oauth.getUserInfo(),
                oauth.getAuthTime(), oauth.getLoginName(), oauth.getDisplayName(), oauth.getNickname(),
                oauth.getAvatarUrl(), oauth.getEmail(), oauth.getProfileUrl(), oauth.getUnionId());
    }
}
