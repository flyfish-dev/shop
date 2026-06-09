package group.flyfish.dev.shop.wechat.service;

public interface WechatService {

    /**
     * 判断是否是session code
     *
     * @param msg 消息
     * @return 结果
     */
    boolean isSessionCode(String msg);

    /**
     * 规范化微信消息或扫码场景值为登录会话码。
     *
     * @param raw 原始消息或场景值
     * @return 登录会话码
     */
    String normalizeSessionCode(String raw);

    /**
     * 标记用户已经扫描登录二维码。
     *
     * @param scene  场景值
     * @param openid 用户标识
     */
    void markScanned(String scene, String openid);

    /**
     * 创建一个已经完成微信确认的登录会话。
     *
     * <p>用于公众号消息场景：消息本身已经可信地携带发送人的 openid，因此无需再让用户输入验证码，
     * 只生成短期有效的一次性链接，点击后由服务端消费该会话完成登录。</p>
     *
     * @param openid 公众号用户 openid
     * @return 登录会话码
     */
    String createConfirmedSession(String openid);

    /**
     * 创建一个已经完成微信确认的登录会话，并指定有效期。
     *
     * @param openid        公众号用户 openid
     * @param expireSeconds 会话有效秒数
     * @return 登录会话码
     */
    String createConfirmedSession(String openid, int expireSeconds);

    /**
     * 更新session
     *
     * @param scene  场景值
     * @param openid 用户标识
     */
    void updateSession(String scene, String openid);
}
