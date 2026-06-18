package group.flyfish.dev.customer.service;

import group.flyfish.dev.customer.domain.po.CustomerWechatActivity;
import group.flyfish.dev.customer.domain.vo.CustomerWechatActivityVo;
import group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerWechatActivityService {

    Mono<CustomerWechatActivity> recordInbound(WechatInboundMessage message, String rawXml);

    Flux<CustomerWechatActivityVo> getManagementActivities(PortalUserVo user, String keyword,
                                                           String activityType, int limit);
}
