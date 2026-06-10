package group.flyfish.dev.shop.wechat.protocol;

import group.flyfish.dev.shop.wechat.config.WechatMpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatProtocolTest {

    private static final String APP_ID = "WX_MP_APP_ID";
    private static final String TOKEN = "WX_MP_TOKEN";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void parsesTextMessageWithSpringDomUtils() {
        WechatXmlCodec codec = new WechatXmlCodec();

        WechatInboundMessage message = codec.parseInbound("""
                <xml>
                  <ToUserName><![CDATA[gh-test]]></ToUserName>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <CreateTime>1778752145</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[购买]]></Content>
                </xml>
                """);

        assertEquals("gh-test", message.getToUserName());
        assertEquals("openid-1", message.getFromUserName());
        assertEquals("text", message.getMsgType());
        assertEquals("购买", message.getContent());
    }

    @Test
    void writesImageReplyWithMediaId() {
        WechatXmlCodec codec = new WechatXmlCodec();
        WechatInboundMessage inbound = WechatInboundMessage.builder()
                .toUserName("gh-test")
                .fromUserName("openid-1")
                .build();

        String xml = codec.toXml(WechatReplyMessage.image(inbound, "support-media-id"));

        assertTrue(xml.contains("<MsgType><![CDATA[image]]></MsgType>"));
        assertTrue(xml.contains("<MediaId><![CDATA[support-media-id]]></MediaId>"));
    }

    @Test
    void encryptsAndDecryptsWechatAesMessage() {
        WechatXmlCodec codec = new WechatXmlCodec();
        WechatMessageCrypto crypto = new WechatMessageCrypto(properties(), codec);
        String plainXml = """
                <xml>
                  <ToUserName><![CDATA[gh-test]]></ToUserName>
                  <FromUserName><![CDATA[openid-1]]></FromUserName>
                  <CreateTime>1778752145</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[我要开通]]></Content>
                </xml>
                """;

        String encryptedXml = crypto.encryptXml(plainXml);
        String encrypted = codec.extractEncrypt(encryptedXml);
        String timestamp = encryptedXml.replaceAll("(?s).*<TimeStamp>(.*?)</TimeStamp>.*", "$1");
        String nonce = encryptedXml.replaceAll("(?s).*<Nonce><!\\[CDATA\\[(.*?)]]></Nonce>.*", "$1");
        String signature = encryptedXml.replaceAll("(?s).*<MsgSignature><!\\[CDATA\\[(.*?)]]></MsgSignature>.*", "$1");

        assertTrue(encrypted.length() > 32);
        assertEquals(plainXml, crypto.decryptXml(encryptedXml, signature, timestamp, nonce));
    }

    private WechatMpProperties properties() {
        WechatMpProperties properties = new WechatMpProperties();
        properties.setAppId(APP_ID);
        properties.setToken(TOKEN);
        properties.setAesKey(AES_KEY);
        return properties;
    }
}
