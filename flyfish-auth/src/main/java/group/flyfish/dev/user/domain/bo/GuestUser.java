package group.flyfish.dev.user.domain.bo;

import group.flyfish.dev.user.domain.vo.PortalUserVo;

public class GuestUser {

    private static final PortalUserVo INSTANCE = new PortalUserVo();

    static {
        INSTANCE.setId(-1L);
        INSTANCE.setUsername("未登录用户");
    }

    public static PortalUserVo instance() {
        return INSTANCE;
    }
}
