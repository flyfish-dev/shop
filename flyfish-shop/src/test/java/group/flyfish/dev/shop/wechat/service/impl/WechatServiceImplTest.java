package group.flyfish.dev.shop.wechat.service.impl;

import group.flyfish.dev.wechat.bean.WechatLoginSession;
import group.flyfish.dev.wechat.service.impl.MemoryLoginStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatServiceImplTest {

    @Test
    void createsConfirmedSessionForWechatMessageQuickLogin() {
        MemoryLoginStorage storage = new MemoryLoginStorage();
        WechatServiceImpl service = new WechatServiceImpl(storage);

        String scene = service.createConfirmedSession("wechat-openid", 900);
        WechatLoginSession session = storage.get(scene);

        assertNotNull(session);
        assertTrue(session.isSuccess());
        assertEquals("wechat-openid", session.getOpenid());
        assertEquals("wechat-openid", session.getScannedOpenid());
    }
}
