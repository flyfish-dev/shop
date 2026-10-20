package group.flyfish.dev.xianyu;

import group.flyfish.dev.common.bridge.*;
import group.flyfish.dev.xianyu.top.*;
import group.flyfish.dev.xianyu.security.TokenCipher;
import group.flyfish.dev.xianyu.trade.*;
import group.flyfish.dev.xianyu.store.XianyuModels.Order;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void rejectsTamperingAndExpiredBridgeTickets() {
        String key="local-test-key-012345678901234567890";
        BridgeRequest signed=BridgeSignature.sign("/internal/xianyu/shop/paid","{}",key);
        assertDoesNotThrow(()->BridgeSignature.verify(signed,signed.path(),key,Instant.now()));
        assertThrows(IllegalArgumentException.class,()->BridgeSignature.verify(new BridgeRequest(signed.path(),signed.timestamp(),signed.nonce(),"{\"amount\":0}",signed.signature()),signed.path(),key,Instant.now()));
        assertThrows(IllegalArgumentException.class,()->BridgeSignature.verify(signed,"/internal/xianyu/auth/login",key,Instant.now()));
        assertThrows(IllegalArgumentException.class,()->BridgeSignature.verify(signed,signed.path(),key,Instant.now().plusSeconds(61)));
    }
    @Test void protectsStoredTokensAndRejectsWrongKeys() {
        XianyuProperties properties=new XianyuProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        TokenCipher cipher=new TokenCipher(properties);
        String a=cipher.encrypt("test-token"),b=cipher.encrypt("test-token");
        assertNotEquals(a,b); assertFalse(a.contains("test-token"));assertEquals("test-token",cipher.decrypt(a));
        byte[] wrong=new byte[32];wrong[0]=1;properties.setEncryptionKey(Base64.getEncoder().encodeToString(wrong));
        assertThrows(IllegalStateException.class,()->cipher.decrypt(a));
    }
    @Test void rejectsBusinessFailuresBeforeReadingData() {
        assertThrows(TopGateway.TopFailure.class,()->TopGateway.unwrap(TopOperation.MEDIA_UPLOAD,json.readTree("{\"alibaba_idle_isv_media_upload_response\":{\"result\":{\"success\":false,\"data\":123}}}")));
        assertThrows(TopGateway.TopFailure.class,()->TopGateway.unwrap(TopOperation.ORDER_QUERY,json.readTree("{\"error_response\":{\"code\":50}}")));
        assertThrows(TopGateway.TopFailure.class,()->TopGateway.unwrap(TopOperation.ITEM_PUBLISH,json.readTree("{}")));
        assertEquals("3318740388015865620",TopGateway.unwrap(TopOperation.ORDER_CREATE,json.readTree("{\"alibaba_idle_isv_goosefish_order_create_response\":{\"api_success\":true,\"data\":{\"biz_order_id\":3318740388015865620}}}")).path("data").path("biz_order_id").asText());
    }
    @Test void preservesRefundsAcrossStaleForwardEventsAndRejectsWrongAmounts() {
        Order paid=new Order("k","s","local","3318740388015865620","buyer","XIANYU_MINIAPP",1990,"CREATED",2,"NONE",0,"WAITING","WAITING",Map.of(),1,0);
        Order refunded=OrderTransitions.observe(paid,5,1990,Map.of());
        assertEquals("REFUNDED",refunded.refundState());assertEquals(refunded,OrderTransitions.observe(refunded,2,1990,Map.of()));
        assertThrows(IllegalStateException.class,()->OrderTransitions.observe(paid,2,1989,Map.of()));
        assertEquals(2,OrderTransitions.observe(paid,1,1990,Map.of()).platformState());
        Order closed=OrderTransitions.observe(paid,6,1990,Map.of());
        assertEquals(2,OrderTransitions.observe(closed,2,1990,Map.of()).platformState());
    }
    @Test void validatesExactMoneyAndSkuLimits() {
        assertEquals(1990,ListingValidator.money("19.90"));
        assertThrows(ArithmeticException.class,()->ListingValidator.money("19.901"));
        Map<String,Object> listing=new java.util.LinkedHashMap<>(Map.of("title","数字服务","desc","软件交付","channel_cat_id","123","reserve_price","19.90","images",List.of("123"),"quantity",1));
        assertDoesNotThrow(()->ListingValidator.validate(listing));
        listing.put("title","标题".repeat(16));assertThrows(IllegalArgumentException.class,()->ListingValidator.validate(listing));
    }
    @Test void guardsUnapprovedProductionSales() {
        XianyuProperties properties=new XianyuProperties(); properties.setSalesEnabled(true);
        assertThrows(IllegalStateException.class,properties::requireSales);
        properties.setTowerDeployment(true);properties.setDataFlowApproved(true);
        properties.setProcessingEnabled(true);
        assertDoesNotThrow(properties::requireSales);
    }
}
