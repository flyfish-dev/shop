package group.flyfish.dev.customer.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto;
import group.flyfish.dev.customer.domain.vo.CustomerConversationDetailVo;
import group.flyfish.dev.customer.domain.vo.CustomerConversationVo;
import group.flyfish.dev.customer.domain.vo.CustomerServiceSummaryVo;
import group.flyfish.dev.customer.domain.vo.CustomerWechatActivityVo;
import group.flyfish.dev.customer.service.CustomerServiceCenterService;
import group.flyfish.dev.customer.service.CustomerWechatActivityService;
import group.flyfish.dev.user.domain.vo.PortalUserVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Validated
@RestController
@RequestMapping("/portal/customer-service")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceCenterService customerServiceCenterService;

    private final CustomerWechatActivityService customerWechatActivityService;

    @GetMapping("summary")
    public Mono<Result<CustomerServiceSummaryVo>> summary(@CurrentUser PortalUserVo user) {
        return customerServiceCenterService.summary(user).map(Result::ok);
    }

    @GetMapping("conversation")
    public Mono<Result<CustomerConversationDetailVo>> getMyConversation(@CurrentUser PortalUserVo user) {
        return customerServiceCenterService.getMyConversation(user).map(Result::ok);
    }

    @PostMapping("conversation/read")
    public Mono<Result<Void>> markUserRead(@CurrentUser PortalUserVo user) {
        return customerServiceCenterService.markUserRead(user).thenReturn(Result.ok());
    }

    @PostMapping("messages")
    public Mono<Result<CustomerConversationDetailVo>> sendMyMessage(@CurrentUser PortalUserVo user,
                                                                    @Valid @RequestBody CustomerMessageSendDto dto) {
        return customerServiceCenterService.sendMyMessage(user, dto).map(Result::ok);
    }

    @GetMapping("management/conversations")
    public Mono<Result<List<CustomerConversationVo>>> managementConversations(@CurrentUser PortalUserVo user,
                                                                              @RequestParam(required = false) String keyword) {
        return customerServiceCenterService.getManagementConversations(user, keyword)
                .collectList()
                .map(Result::ok);
    }

    @GetMapping("management/wechat-activities")
    public Mono<Result<List<CustomerWechatActivityVo>>> managementWechatActivities(
            @CurrentUser PortalUserVo user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String activityType,
            @RequestParam(defaultValue = "80") int limit) {
        return customerWechatActivityService.getManagementActivities(user, keyword, activityType, limit)
                .collectList()
                .map(Result::ok);
    }

    @GetMapping("management/conversations/{conversationId}")
    public Mono<Result<CustomerConversationDetailVo>> managementConversation(@CurrentUser PortalUserVo user,
                                                                            @PathVariable Long conversationId) {
        return customerServiceCenterService.getManagementConversation(user, conversationId).map(Result::ok);
    }

    @GetMapping("management/users/{userId}/conversation")
    public Mono<Result<CustomerConversationDetailVo>> managementConversationByUser(@CurrentUser PortalUserVo user,
                                                                                  @PathVariable Long userId) {
        return customerServiceCenterService.getManagementConversationByUser(user, userId).map(Result::ok);
    }

    @PostMapping("management/conversations/{conversationId}/messages")
    public Mono<Result<CustomerConversationDetailVo>> sendManagementMessage(@CurrentUser PortalUserVo user,
                                                                           @PathVariable Long conversationId,
                                                                           @Valid @RequestBody CustomerMessageSendDto dto) {
        return customerServiceCenterService.sendManagementMessage(user, conversationId, dto).map(Result::ok);
    }

    @PostMapping("management/conversations/{conversationId}/read")
    public Mono<Result<Void>> markManagementRead(@CurrentUser PortalUserVo user,
                                                 @PathVariable Long conversationId) {
        return customerServiceCenterService.markManagementRead(user, conversationId).thenReturn(Result.ok());
    }
}
