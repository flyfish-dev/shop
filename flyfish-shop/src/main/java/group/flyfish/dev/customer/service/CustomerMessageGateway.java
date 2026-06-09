package group.flyfish.dev.customer.service;

import group.flyfish.dev.customer.domain.po.CustomerMessage;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import reactor.core.publisher.Mono;

public interface CustomerMessageGateway {

    Mono<CustomerMessage> recordInbound(WechatInboundMessage message, String rawXml);
}
