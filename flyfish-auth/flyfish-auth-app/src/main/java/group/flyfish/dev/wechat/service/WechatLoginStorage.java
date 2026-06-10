package group.flyfish.dev.wechat.service;

import group.flyfish.dev.wechat.bean.WechatLoginSession;

/**
 * 微信登录存储
 *
 * @author wangyu
 */
public interface WechatLoginStorage {

    /**
     * 创建场景，返回场景值
     *
     * @return 场景值
     */
    String newId();

    String newId(Long bindUserId);

    /**
     * 通过场景值，获取openid
     * 1. 场景值存在，但是用户未操作，判断过期时间，已过期返回null，未过期返回空字符串
     * 2. 场景值不存在，返回null
     *
     * @param scene 场景值
     * @return 结果
     */
    WechatLoginSession get(String scene);

    /**
     * 标记用户已扫码。
     *
     * @param scene  场景值
     * @param openid 开放id
     * @return 结果
     */
    WechatLoginSession scan(String scene, String openid);

    /**
     * 清空场景值
     *
     * @param scene 场景值
     * @return 结果
     */
    WechatLoginSession clear(String scene);

    /**
     * 设置openid
     *
     * @param scene  场景值
     * @param openid 开放id
     * @return 结果
     */
    WechatLoginSession put(String scene, String openid);
}
