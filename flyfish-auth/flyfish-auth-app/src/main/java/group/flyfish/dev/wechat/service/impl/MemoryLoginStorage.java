package group.flyfish.dev.wechat.service.impl;

import group.flyfish.dev.wechat.bean.WechatLoginSession;
import group.flyfish.dev.wechat.service.WechatLoginStorage;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的场景值维护
 *
 * @author wangyu
 */
@Service
public class MemoryLoginStorage implements WechatLoginStorage {

    private final Map<String, WechatLoginSession> STORE = new ConcurrentHashMap<>();

    /**
     * 创建场景，返回场景值
     *
     * @return 场景值
     */
    @Override
    public String newId() {
        return newId(null);
    }

    @Override
    public String newId(Long bindUserId) {
        String id;
        do {
            id = RandomStringUtils.randomNumeric(6);
        } while (STORE.containsKey(id));
        STORE.put(id, new WechatLoginSession(id, bindUserId));
        return id;
    }

    /**
     * 通过场景值，获取openid
     * 1. 场景值存在，但是用户未操作，判断过期时间，已过期返回null，未过期返回空字符串
     * 2. 场景值不存在，返回null
     * 3. 场景值存在且有值，返回并销毁场景值
     *
     * @param scene 场景值
     * @return 结果
     */
    @Override
    public WechatLoginSession get(String scene) {
        return STORE.computeIfPresent(scene, (key, old) -> old.isExpired() ? null : old);
    }

    @Override
    public WechatLoginSession scan(String scene, String openid) {
        return STORE.computeIfPresent(scene, (key, session) -> {
            if (session.isExpired()) {
                return null;
            }
            session.markScanned(openid);
            return session;
        });
    }

    /**
     * 清空场景值
     *
     * @param scene 场景值
     * @return 结果
     */
    @Override
    public WechatLoginSession clear(String scene) {
        return STORE.remove(scene);
    }

    /**
     * 设置openid
     *
     * @param scene  场景值
     * @param openid 开放id
     * @return 结果
     */
    @Override
    public WechatLoginSession put(String scene, String openid) {
        return STORE.computeIfPresent(scene, (key, session) -> {
            if (session.isExpired()) {
                return null;
            }
            session.confirm(openid);
            return session;
        });
    }
}
