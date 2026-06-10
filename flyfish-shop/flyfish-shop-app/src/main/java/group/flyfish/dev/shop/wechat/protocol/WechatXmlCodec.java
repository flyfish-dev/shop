package group.flyfish.dev.shop.wechat.protocol;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * 公众号 XML 编解码器。
 *
 * <p>只解析当前系统会使用的字段，并使用 JAXP + Spring {@link DomUtils}
 * 完成安全 DOM 读取，不引入反射重的 XML 绑定框架。</p>
 */
@Component
public class WechatXmlCodec {

    public WechatInboundMessage parseInbound(String xml) {
        Element root = parseRoot(xml);
        return WechatInboundMessage.builder()
                .toUserName(childText(root, "ToUserName"))
                .fromUserName(childText(root, "FromUserName"))
                .createTime(childText(root, "CreateTime"))
                .msgType(childText(root, "MsgType"))
                .content(childText(root, "Content"))
                .msgId(childText(root, "MsgId"))
                .picUrl(childText(root, "PicUrl"))
                .mediaId(childText(root, "MediaId"))
                .event(childText(root, "Event"))
                .eventKey(childText(root, "EventKey"))
                .latitude(firstNotBlank(childText(root, "Latitude"), childText(root, "Location_X")))
                .longitude(firstNotBlank(childText(root, "Longitude"), childText(root, "Location_Y")))
                .precision(firstNotBlank(childText(root, "Precision"), childText(root, "Scale")))
                .build();
    }

    public String extractEncrypt(String xml) {
        return childText(parseRoot(xml), "Encrypt");
    }

    public String toXml(WechatReplyMessage message) {
        if (message == null) {
            return "";
        }
        return switch (message.type()) {
            case TEXT -> textXml(message);
            case IMAGE -> imageXml(message);
            case TRANSFER_CUSTOMER_SERVICE -> transferCustomerServiceXml(message);
        };
    }

    private Element parseRoot(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(StringUtils.trimToEmpty(xml))));
            return document.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("微信 XML 解析失败", e);
        }
    }

    private String childText(Element root, String childName) {
        Element child = DomUtils.getChildElementByTagName(root, childName);
        return child == null ? "" : StringUtils.trimToEmpty(DomUtils.getTextValue(child));
    }

    private String firstNotBlank(String first, String second) {
        return StringUtils.defaultIfBlank(first, second);
    }

    private String textXml(WechatReplyMessage message) {
        return """
                <xml>
                  <ToUserName><![CDATA[%s]]></ToUserName>
                  <FromUserName><![CDATA[%s]]></FromUserName>
                  <CreateTime>%d</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[%s]]></Content>
                </xml>
                """.formatted(cdata(message.toUserName()), cdata(message.fromUserName()),
                nowSeconds(), cdata(message.content()));
    }

    private String transferCustomerServiceXml(WechatReplyMessage message) {
        return """
                <xml>
                  <ToUserName><![CDATA[%s]]></ToUserName>
                  <FromUserName><![CDATA[%s]]></FromUserName>
                  <CreateTime>%d</CreateTime>
                  <MsgType><![CDATA[transfer_customer_service]]></MsgType>
                </xml>
                """.formatted(cdata(message.toUserName()), cdata(message.fromUserName()), nowSeconds());
    }

    private String imageXml(WechatReplyMessage message) {
        return """
                <xml>
                  <ToUserName><![CDATA[%s]]></ToUserName>
                  <FromUserName><![CDATA[%s]]></FromUserName>
                  <CreateTime>%d</CreateTime>
                  <MsgType><![CDATA[image]]></MsgType>
                  <Image>
                    <MediaId><![CDATA[%s]]></MediaId>
                  </Image>
                </xml>
                """.formatted(cdata(message.toUserName()), cdata(message.fromUserName()),
                nowSeconds(), cdata(message.content()));
    }

    private long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private String cdata(String value) {
        return StringUtils.defaultString(value).replace("]]>", "]]]]><![CDATA[>");
    }
}
