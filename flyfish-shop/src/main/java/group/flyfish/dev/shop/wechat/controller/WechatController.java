package group.flyfish.dev.shop.wechat.controller;

import group.flyfish.dev.customer.service.CustomerWechatActivityService;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.shop.wechat.protocol.WechatMessageCrypto;
import group.flyfish.dev.shop.wechat.protocol.WechatReplyMessage;
import group.flyfish.dev.shop.wechat.protocol.WechatXmlCodec;
import group.flyfish.dev.shop.wechat.router.WechatMessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 微信公众号控制器
 *
 * @author wangyu
 */
@RestController
@RequestMapping("wx")
@RequiredArgsConstructor
@Slf4j
public class WechatController {

    private static final String NO_REPLY = "";

    private final WechatMessageCrypto messageCrypto;

    private final WechatXmlCodec xmlCodec;

    private final WechatMessageRouter messageRouter;

    private final CustomerWechatActivityService customerWechatActivityService;

    /**
     * 微信认证请求
     *
     * @param signature 签名
     * @param timestamp 时间戳
     * @param nonce     随机串
     * @param echostr   响应字符串
     * @return 结果
     */
    @GetMapping(produces = "text/plain;charset=utf-8")
    public Mono<String> authGet(@RequestParam(name = "signature", required = false) String signature,
                                @RequestParam(name = "timestamp", required = false) String timestamp,
                                @RequestParam(name = "nonce", required = false) String nonce,
                                @RequestParam(name = "echostr", required = false) String echostr) {

        log.info("\n接收到来自微信服务器的认证消息：[{}, {}, {}, {}]", signature,
                timestamp, nonce, echostr);
        if (StringUtils.isAnyBlank(signature, timestamp, nonce, echostr)) {
            throw new IllegalArgumentException("请求参数非法，请核实!");
        }

        if (messageCrypto.checkSignature(timestamp, nonce, signature)) {
            return Mono.just(echostr);
        }

        return Mono.just("非法请求");
    }

    /**
     * 微信消息路由
     *
     * @param requestBody  请求体
     * @param signature    签名
     * @param timestamp    时间戳
     * @param nonce        随机串
     * @param openid       openid
     * @param encType      加密类型
     * @param msgSignature 消息签名
     * @return 结果
     */
    @PostMapping(produces = "application/xml; charset=UTF-8")
    public Mono<String> post(@RequestBody String requestBody,
                             @RequestParam("signature") String signature,
                             @RequestParam("timestamp") String timestamp,
                             @RequestParam("nonce") String nonce,
                             @RequestParam(name = "openid", required = false) String openid,
                             @RequestParam(name = "encrypt_type", required = false) String encType,
                             @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        log.info("""
                接收微信请求：
                [openid=[{}], [signature=[{}], encType=[{}], msgSignature=[{}], timestamp=[{}], nonce=[{}],
                 requestBody=[{}]\s
                     """, openid, signature, encType, msgSignature, timestamp, nonce, requestBody);

        if (!messageCrypto.checkSignature(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("非法请求，可能属于伪造的请求！");
        }

        boolean encrypted = "aes".equalsIgnoreCase(encType);
        return Mono.defer(() -> {
                    String plainXml = encrypted
                            ? messageCrypto.decryptXml(requestBody, msgSignature, timestamp, nonce)
                            : requestBody;
                    WechatInboundMessage inMessage = xmlCodec.parseInbound(plainXml);
                    log.debug("\n微信消息明文内容为：\n{} ", plainXml);
                    return customerWechatActivityService.recordInbound(inMessage, plainXml)
                            .onErrorResume(e -> {
                                log.warn("微信用户动态入库失败，继续执行消息路由。openid={}, error={}",
                                        inMessage.getFromUserName(), e.getMessage());
                                return Mono.empty();
                            })
                            .then(messageRouter.route(inMessage))
                            .onErrorResume(e -> {
                                log.error("路由微信消息时出现异常！", e);
                                return Mono.empty();
                            })
                            .map(this::toXml)
                            .map(out -> encrypted ? messageCrypto.encryptXml(out) : out)
                            .defaultIfEmpty(NO_REPLY);
                })
                .doOnNext(out -> log.debug("\n组装回复信息：{}", out));
    }

    private String toXml(WechatReplyMessage outMessage) {
        return xmlCodec.toXml(outMessage);
    }

}
