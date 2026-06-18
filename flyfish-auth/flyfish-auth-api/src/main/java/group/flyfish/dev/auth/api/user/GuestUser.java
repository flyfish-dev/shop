package group.flyfish.dev.auth.api.user;

/**
 * 未登录用户占位对象。
 */
public final class GuestUser extends PortalUserVo {

    private static final GuestUser INSTANCE = new GuestUser();

    private GuestUser() {
        setId(0L);
        setUsername("游客");
    }

    public static GuestUser instance() {
        return INSTANCE;
    }
}
