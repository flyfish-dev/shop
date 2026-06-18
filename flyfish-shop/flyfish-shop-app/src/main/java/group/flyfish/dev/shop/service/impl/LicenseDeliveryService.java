package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopLicenseRoot;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
import group.flyfish.dev.shop.license.ExternalLicenseIssuer;
import group.flyfish.dev.shop.license.IssuedLicenseDocument;
import group.flyfish.dev.shop.repository.ShopLicenseKeyPairRepository;
import group.flyfish.dev.shop.repository.ShopLicenseRootRepository;
import group.flyfish.dev.shop.repository.ShopOrderDeliveryRepository;
import group.flyfish.dev.shop.service.DeliveryResult;
import group.flyfish.dev.shop.service.ShopDeliveryHandler;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class LicenseDeliveryService implements ShopDeliveryHandler {

    private static final String ACTOR = "auto-delivery";
    private static final String ROOT_NAME = "external-license-provider";
    private static final String ALGORITHM = "EXTERNAL";
    private static final String PRIVATE_MATERIAL_NOT_EXPORTED = "[external-license-private-material-not-exported]";

    private final ShopLicenseRootRepository rootRepository;
    private final ShopLicenseKeyPairRepository keyPairRepository;
    private final ShopOrderDeliveryRepository deliveryRepository;
    private final ExternalLicenseIssuer licenseIssuer;

    @Override
    public boolean supports(ShopItem item) {
        return item != null && ShopItemDeliveryPlan.hasAction(item, ShopDeliveryAction.LICENSE);
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        LicenseDeliveryParamValue param = ShopItemParamValue.licenseDelivery(item.getParams(), item.getName());
        return keyPairRepository.findByOrderNo(order.getOrderNo())
                .flatMap(existing -> upsertDelivery(order, item, param, existing)
                        .thenReturn(DeliveryResult.ok("授权许可已签发，可在我的订单中提取授权文件")))
                .switchIfEmpty(Mono.defer(() -> defaultRoot()
                        .flatMap(root -> issueLicense(order, item, buyer, param, root))
                        .flatMap(license -> upsertDelivery(order, item, param, license)
                                .thenReturn(DeliveryResult.ok("授权许可已签发，可在我的订单中提取授权文件")))));
    }

    private Mono<ShopLicenseRoot> defaultRoot() {
        return rootRepository.findByName(ROOT_NAME)
                .switchIfEmpty(Mono.fromCallable(this::createRoot).flatMap(rootRepository::save));
    }

    private ShopLicenseRoot createRoot() {
        ShopLicenseRoot root = new ShopLicenseRoot();
        root.setName(ROOT_NAME);
        root.setAlgorithm(ALGORITHM);
        root.setPublicKey(ROOT_NAME);
        root.setPrivateKey(PRIVATE_MATERIAL_NOT_EXPORTED);
        root.setCreateBy(ACTOR);
        root.setUpdateBy(ACTOR);
        return root;
    }

    private Mono<ShopLicenseKeyPair> issueLicense(ShopOrder order, ShopItem item, PortalUserVo buyer,
                                                  LicenseDeliveryParamValue param, ShopLicenseRoot root) {
        return Mono.fromCallable(() -> {
            String licenseNo = "LIC" + IdGenerators.idString();
            IssuedLicenseDocument issued = licenseIssuer.issue(licenseNo, order, item, buyer, param);

            ShopLicenseKeyPair license = new ShopLicenseKeyPair();
            license.setLicenseNo(licenseNo);
            license.setOrderNo(order.getOrderNo());
            license.setItemId(item.getId());
            license.setBuyerId(order.getBuyerId());
            license.setRootId(root.getId());
            license.setAlgorithm(issued.providerAlgorithm());
            license.setPublicKey(issued.providerKeyId());
            license.setPrivateKey(PRIVATE_MATERIAL_NOT_EXPORTED);
            license.setCertificate(issued.payloadJson());
            license.setSignature(issued.licenseJson());
            license.setCreateBy(ACTOR);
            license.setUpdateBy(ACTOR);
            return license;
        }).flatMap(keyPairRepository::save);
    }

    private Mono<ShopOrderDelivery> upsertDelivery(ShopOrder order, ShopItem item,
                                                   LicenseDeliveryParamValue param,
                                                   ShopLicenseKeyPair license) {
        return deliveryRepository.findByOrderNo(order.getOrderNo())
                .defaultIfEmpty(new ShopOrderDelivery())
                .map(delivery -> {
                    applyDelivery(delivery, order, item, param, license);
                    return delivery;
                })
                .flatMap(deliveryRepository::save)
                .onErrorMap(e -> new ServiceException("授权许可交付快照保存失败", e));
    }

    private void applyDelivery(ShopOrderDelivery delivery, ShopOrder order, ShopItem item,
                               LicenseDeliveryParamValue param, ShopLicenseKeyPair license) {
        if (delivery.getId() == null) {
            delivery.setCreateBy(ACTOR);
        }
        delivery.setUpdateBy(ACTOR);
        delivery.setOrderNo(order.getOrderNo());
        delivery.setItemId(item.getId());
        delivery.setBuyerId(order.getBuyerId());
        delivery.setDeliveryType(ShopOrderDelivery.DeliveryType.LICENSE.name());
        delivery.setTitle(StringUtils.left(param.getLicenseName(), 128));
        delivery.setLicenseNo(license.getLicenseNo());
        delivery.setContent(deliveryContent(param, license));
    }

    private String deliveryContent(LicenseDeliveryParamValue param, ShopLicenseKeyPair license) {
        return """
                授权编号：%s
                授权名称：%s
                授权范围：%s
                授权套餐：%s
                授权域名：%s

                授权文件已签发。请在我的订单中下载 license.lic 或 license.json 后部署使用。
                为降低泄露风险，页面不会直接展示授权正文，请勿通过聊天、截图或公开页面传播授权文件。
                """.formatted(license.getLicenseNo(), param.getLicenseName(), param.getScope(),
                param.getEdition(), String.join("，", param.getAllowedOrigins()));
    }
}
