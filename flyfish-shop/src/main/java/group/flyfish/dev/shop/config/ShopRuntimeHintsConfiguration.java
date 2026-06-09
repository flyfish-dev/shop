package group.flyfish.dev.shop.config;

import group.flyfish.dev.common.config.RuntimeHintsSupport;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ShopRuntimeHintsConfiguration.ShopRuntimeHints.class)
public class ShopRuntimeHintsConfiguration {

    public static class ShopRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.shop.service.support.h5zhifu.H5ZhiFuService");
            RuntimeHintsSupport.registerHttpInterfaceProxyIfPresent(hints, classLoader,
                    "group.flyfish.dev.shop.wechat.client.WechatMpClient");
            RuntimeHintsSupport.registerConfigurationProperties(hints, classLoader,
                    "group.flyfish.dev.shop.service.support.h5zhifu.config.H5ZhiFuProperties",
                    "group.flyfish.dev.support.notification.SupportNotificationProperties",
                    "group.flyfish.dev.support.notification.SupportNotificationProperties$Mail",
                    "group.flyfish.dev.support.notification.SupportNotificationProperties$Wechat",
                    "group.flyfish.dev.shop.wechat.config.WechatMpProperties",
                    "group.flyfish.dev.shop.wechat.config.WechatQuickLoginProperties");
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.shop.converter.ShopItemParamValue",
                    "group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue",
                    "group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue$Repository",
                    "group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue",
                    "group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue",
                    "group.flyfish.dev.shop.domain.dto.ShopCouponApplyDto",
                    "group.flyfish.dev.shop.domain.dto.ShopCouponCreateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopCouponUpdateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopCreateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopItemCreateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopItemGroupCreateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopItemGroupUpdateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopItemUpdateDto",
                    "group.flyfish.dev.shop.domain.dto.ShopOrderDeliveryDto",
                    "group.flyfish.dev.shop.domain.dto.ShopOrderDto",
                    "group.flyfish.dev.shop.domain.dto.ShopUpdateDto",
                    "group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair",
                    "group.flyfish.dev.shop.domain.po.ShopLicenseRoot",
                    "group.flyfish.dev.shop.domain.po.ShopOrderDelivery",
                    "group.flyfish.dev.shop.domain.qo.ShopItemGroupListQo",
                    "group.flyfish.dev.shop.domain.qo.ShopItemListQo",
                    "group.flyfish.dev.shop.domain.qo.ShopItemListQo$Order",
                    "group.flyfish.dev.shop.domain.qo.ShopItemPageQo",
                    "group.flyfish.dev.shop.domain.vo.ShopOrderDeliveryExtractVo",
                    "group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuNotifyDto",
                    "group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayDto",
                    "group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuPayResultDto",
                    "group.flyfish.dev.shop.service.support.h5zhifu.bean.H5ZhiFuResultDto",
                    "group.flyfish.dev.customer.domain.dto.CustomerMessageSendDto",
                    "group.flyfish.dev.customer.domain.dto.CustomerSocketCommand",
                    "group.flyfish.dev.customer.domain.po.CustomerConversation",
                    "group.flyfish.dev.customer.domain.po.CustomerMessage",
                    "group.flyfish.dev.customer.domain.vo.CustomerConversationDetailVo",
                    "group.flyfish.dev.customer.domain.vo.CustomerConversationVo",
                    "group.flyfish.dev.customer.domain.vo.CustomerMessageVo",
                    "group.flyfish.dev.customer.domain.vo.CustomerSocketEnvelopeVo",
                    "group.flyfish.dev.customer.domain.vo.CustomerServiceSummaryVo",
                    "group.flyfish.dev.support.domain.dto.SupportTicketCreateDto",
                    "group.flyfish.dev.support.domain.dto.SupportTicketMessageDto",
                    "group.flyfish.dev.support.domain.po.SupportTicket",
                    "group.flyfish.dev.support.domain.po.SupportTicketMessage",
                    "group.flyfish.dev.support.domain.vo.SupportTicketDetailVo",
                    "group.flyfish.dev.support.domain.vo.SupportTicketMessageVo",
                    "group.flyfish.dev.support.domain.vo.SupportTicketVo",
                    "group.flyfish.dev.shop.wechat.client.WechatAccessTokenResponse",
                    "group.flyfish.dev.shop.wechat.client.WechatCustomMessageRequest",
                    "group.flyfish.dev.shop.wechat.client.WechatCustomMessageRequest$Text",
                    "group.flyfish.dev.shop.wechat.client.WechatKfOnlineListResponse",
                    "group.flyfish.dev.shop.wechat.client.WechatKfOnlineListResponse$KfOnline",
                    "group.flyfish.dev.shop.wechat.client.WechatQrCodeRequest",
                    "group.flyfish.dev.shop.wechat.client.WechatQrCodeTicketResponse",
                    "group.flyfish.dev.shop.wechat.client.WechatSimpleApiResponse",
                    "group.flyfish.dev.shop.wechat.client.WechatUserInfoResponse",
                    "group.flyfish.dev.shop.wechat.protocol.WechatInboundMessage",
                    "group.flyfish.dev.shop.wechat.protocol.WechatReplyMessage");
        }
    }
}
