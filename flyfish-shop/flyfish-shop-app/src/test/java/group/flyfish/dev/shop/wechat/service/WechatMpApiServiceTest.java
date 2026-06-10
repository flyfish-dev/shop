package group.flyfish.dev.shop.wechat.service;

import group.flyfish.dev.shop.wechat.client.WechatMpClient;
import group.flyfish.dev.shop.wechat.config.WechatMpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class WechatMpApiServiceTest {

    @Test
    void customerSupportImageUsesConfiguredMediaIdFirst() {
        WechatMpClient client = mock(WechatMpClient.class);
        WechatMpProperties properties = new WechatMpProperties();
        properties.setCustomerServiceMediaId("fixed-media-id");
        WechatMpApiService service = new WechatMpApiService(client, properties);

        assertEquals("fixed-media-id", service.customerSupportImageMediaId().block());
        verifyNoInteractions(client);
    }

    @Test
    void customerSupportImageRequiresMediaId() {
        WechatMpClient client = mock(WechatMpClient.class);
        WechatMpProperties properties = new WechatMpProperties();
        WechatMpApiService service = new WechatMpApiService(client, properties);

        assertThrows(IllegalStateException.class, () -> service.customerSupportImageMediaId().block());
        verifyNoInteractions(client);
    }
}
