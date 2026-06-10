package group.flyfish.dev.user.domain.vo;

import group.flyfish.dev.auth.api.user.OAuthType;
import group.flyfish.dev.auth.api.user.PortalUserOauthVo;
import group.flyfish.dev.user.domain.po.PortalUserOauth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalUserOauthVoTest {

    @Test
    void prefersNormalizedColumnsOverRawSnapshot() {
        PortalUserOauth oauth = new PortalUserOauth();
        oauth.setType(OAuthType.GITHUB);
        oauth.setOpenid("123");
        oauth.setUserInfo("{\"login\":\"old-login\",\"display_name\":\"Old Name\",\"email\":\"old@example.com\"}");
        oauth.setLoginName("octocat");
        oauth.setDisplayName("Mona Octocat");
        oauth.setNickname("Mona");
        oauth.setAvatarUrl("https://avatars.githubusercontent.com/u/1");
        oauth.setEmail("mona@example.com");
        oauth.setProfileUrl("https://github.com/octocat");

        PortalUserOauthVo vo = oauthVo(oauth);

        assertEquals("octocat", vo.getLogin());
        assertEquals("Mona Octocat", vo.getDisplayName());
        assertEquals("Mona", vo.getNickname());
        assertEquals("https://avatars.githubusercontent.com/u/1", vo.getAvatar());
        assertEquals("mona@example.com", vo.getEmail());
        assertEquals("https://github.com/octocat", vo.getProfileUrl());
    }

    @Test
    void fallsBackToProviderSnapshotForLegacyRows() {
        PortalUserOauth oauth = new PortalUserOauth();
        oauth.setType(OAuthType.WECHAT);
        oauth.setOpenid("openid-1234567890");
        oauth.setUserInfo("""
                {
                  "nickname": "微信昵称",
                  "headimgurl": "https://wx.example/avatar.jpg",
                  "unionid": "union-1"
                }
                """);

        PortalUserOauthVo vo = oauthVo(oauth);

        assertEquals("微信昵称", vo.getDisplayName());
        assertEquals("https://wx.example/avatar.jpg", vo.getAvatar());
        assertEquals("union-1", vo.getUnionId());
    }

    private static PortalUserOauthVo oauthVo(PortalUserOauth oauth) {
        return PortalUserOauthVo.of(oauth.getUserId(), oauth.getType(), oauth.getOpenid(), oauth.getUserInfo(),
                oauth.getAuthTime(), oauth.getLoginName(), oauth.getDisplayName(), oauth.getNickname(),
                oauth.getAvatarUrl(), oauth.getEmail(), oauth.getProfileUrl(), oauth.getUnionId());
    }
}
