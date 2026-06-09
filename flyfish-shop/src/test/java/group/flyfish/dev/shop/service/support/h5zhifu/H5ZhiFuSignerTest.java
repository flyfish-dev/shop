package group.flyfish.dev.shop.service.support.h5zhifu;

import group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H5ZhiFuSignerTest {

    @Test
    void signsOfficialExampleWithAsciiSortedFields() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("app_id", 12345);
        params.put("amount", 1);
        params.put("out_trade_no", "123456789");

        assertEquals("FBDA8CE40017F62D2A2F6CC1F1D85F7D", H5ZhiFuSigner.sign(params, "xxxxxxxxx"));
    }

    @Test
    void ignoresSignAndBlankValuesWhenSigningCallbackPayload() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sign", "SHOULD_NOT_JOIN");
        params.put("empty", "");
        params.put("nullable", null);
        params.put("out_trade_no", "FF100");
        params.put("amount", 1990);

        assertEquals(
                H5ZhiFuSigner.sign(Map.of("amount", 1990, "out_trade_no", "FF100"), "secret"),
                H5ZhiFuSigner.sign(params, "secret"));
    }

    @Test
    void usesJsonPropertyNamesForOutboundBeanFields() {
        H5ZhiFuPayDto dto = new H5ZhiFuPayDto();
        dto.setAppId(10001L);
        dto.setOutTradeNo("FF200");
        dto.setDescription("商品");
        dto.setPayType(H5ZhiFuPayDto.PayType.wechat);
        dto.setAmount(1);
        dto.setAttach("FF200");
        dto.setNotifyUrl("https://api.flyfish.group/shops/payments/h5zhifu/notify");

        Map<String, Object> samePayload = new LinkedHashMap<>();
        samePayload.put("app_id", 10001L);
        samePayload.put("out_trade_no", "FF200");
        samePayload.put("description", "商品");
        samePayload.put("pay_type", "wechat");
        samePayload.put("amount", 1);
        samePayload.put("attach", "FF200");
        samePayload.put("notify_url", "https://api.flyfish.group/shops/payments/h5zhifu/notify");

        assertEquals(H5ZhiFuSigner.sign(samePayload, "secret"), H5ZhiFuSigner.sign(dto, "secret"));
    }
}
