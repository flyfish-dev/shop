package group.flyfish.dev.customer.service;

import group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto;
import group.flyfish.dev.customer.domain.vo.CustomerConversationDetailVo;
import group.flyfish.dev.customer.domain.vo.CustomerConversationVo;
import group.flyfish.dev.customer.domain.vo.CustomerServiceSummaryVo;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CustomerServiceCenterService {

    Mono<CustomerServiceSummaryVo> summary(PortalUserVo user);

    Mono<CustomerConversationDetailVo> getMyConversation(PortalUserVo user);

    Mono<CustomerConversationDetailVo> peekMyConversation(PortalUserVo user);

    Mono<CustomerConversationDetailVo> sendMyMessage(PortalUserVo user, CustomerMessageSendDto dto);

    Flux<CustomerConversationVo> getManagementConversations(PortalUserVo user, String keyword);

    Mono<CustomerConversationDetailVo> getManagementConversation(PortalUserVo user, Long conversationId);

    Mono<CustomerConversationDetailVo> peekManagementConversation(PortalUserVo user, Long conversationId);

    Mono<CustomerConversationDetailVo> getManagementConversationByUser(PortalUserVo user, Long targetUserId);

    Mono<CustomerConversationDetailVo> sendManagementMessage(PortalUserVo user, Long conversationId,
                                                             CustomerMessageSendDto dto);

    Mono<Void> markManagementRead(PortalUserVo user, Long conversationId);

    Mono<Void> markUserRead(PortalUserVo user);
}
