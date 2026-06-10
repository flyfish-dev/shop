package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.utils.IdGenerators;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopLicenseKeyPair;
import group.flyfish.dev.shop.domain.po.ShopLicenseRoot;
import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.domain.po.ShopOrderDelivery;
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

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LicenseDeliveryService implements ShopDeliveryHandler {

    private static final String ACTOR = "auto-delivery";
    private static final String ROOT_NAME = "flyfish-default-root";
    private static final String ALGORITHM = "Ed25519";

    private final ShopLicenseRootRepository rootRepository;
    private final ShopLicenseKeyPairRepository keyPairRepository;
    private final ShopOrderDeliveryRepository deliveryRepository;

    @Override
    public boolean supports(ShopItem item) {
        return item != null && item.getType() == ShopItem.Type.LICENSE;
    }

    @Override
    public Mono<DeliveryResult> deliver(ShopOrder order, ShopItem item, PortalUserVo buyer) {
        LicenseDeliveryParamValue param = ShopItemParamValue.licenseDelivery(item.getParams(), item.getName());
        return keyPairRepository.findByOrderNo(order.getOrderNo())
                .flatMap(existing -> upsertDelivery(order, item, param, existing)
                        .thenReturn(DeliveryResult.ok("授权许可已签发，可在我的订单中提取密钥")))
                .switchIfEmpty(Mono.defer(() -> defaultRoot()
                        .flatMap(root -> issueLicense(order, item, buyer, param, root))
                        .flatMap(license -> upsertDelivery(order, item, param, license)
                                .thenReturn(DeliveryResult.ok("授权许可已签发，可在我的订单中提取密钥")))));
    }

    private Mono<ShopLicenseRoot> defaultRoot() {
        return rootRepository.findByName(ROOT_NAME)
                .switchIfEmpty(Mono.fromCallable(this::createRoot).flatMap(rootRepository::save));
    }

    private ShopLicenseRoot createRoot() {
        KeyPair keyPair = generateKeyPair();
        ShopLicenseRoot root = new ShopLicenseRoot();
        root.setName(ROOT_NAME);
        root.setAlgorithm(ALGORITHM);
        root.setPublicKey(encode(keyPair.getPublic()));
        root.setPrivateKey(encode(keyPair.getPrivate()));
        root.setCreateBy(ACTOR);
        root.setUpdateBy(ACTOR);
        return root;
    }

    private Mono<ShopLicenseKeyPair> issueLicense(ShopOrder order, ShopItem item, PortalUserVo buyer,
                                                  LicenseDeliveryParamValue param, ShopLicenseRoot root) {
        return Mono.fromCallable(() -> {
            KeyPair licenseKeyPair = generateKeyPair();
            String licenseNo = "LIC" + IdGenerators.idString();
            Map<String, Object> certificate = certificatePayload(order, item, buyer, param, root, licenseNo,
                    licenseKeyPair);
            String certificateJson = JacksonUtils.toJson(certificate);
            String signature = sign(certificateJson, root.getPrivateKey());

            ShopLicenseKeyPair license = new ShopLicenseKeyPair();
            license.setLicenseNo(licenseNo);
            license.setOrderNo(order.getOrderNo());
            license.setItemId(item.getId());
            license.setBuyerId(order.getBuyerId());
            license.setRootId(root.getId());
            license.setAlgorithm(ALGORITHM);
            license.setPublicKey(encode(licenseKeyPair.getPublic()));
            license.setPrivateKey(encode(licenseKeyPair.getPrivate()));
            license.setCertificate(certificateJson);
            license.setSignature(signature);
            license.setCreateBy(ACTOR);
            license.setUpdateBy(ACTOR);
            return license;
        }).flatMap(keyPairRepository::save);
    }

    private Map<String, Object> certificatePayload(ShopOrder order, ShopItem item, PortalUserVo buyer,
                                                   LicenseDeliveryParamValue param, ShopLicenseRoot root,
                                                   String licenseNo, KeyPair licenseKeyPair) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("licenseNo", licenseNo);
        payload.put("orderNo", order.getOrderNo());
        payload.put("itemId", item.getId());
        payload.put("itemName", item.getName());
        payload.put("buyerId", order.getBuyerId());
        payload.put("buyerName", buyer == null ? null : buyer.getUsername());
        payload.put("licenseName", param.getLicenseName());
        payload.put("scope", param.getScope());
        payload.put("issuedAt", LocalDateTime.now().toString());
        payload.put("expiresAt", expiresAt(param));
        payload.put("algorithm", ALGORITHM);
        payload.put("publicKey", encode(licenseKeyPair.getPublic()));
        payload.put("rootName", root.getName());
        payload.put("rootPublicKey", root.getPublicKey());
        return payload;
    }

    private String expiresAt(LicenseDeliveryParamValue param) {
        if (param.getValidDays() == null) {
            return null;
        }
        return LocalDate.now().plusDays(param.getValidDays()).toString();
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

                授权公钥：
                %s

                授权私钥：
                %s

                授权证书：
                %s

                根证书签名：
                %s
                """.formatted(license.getLicenseNo(), param.getLicenseName(), param.getScope(),
                license.getPublicKey(), license.getPrivateKey(), license.getCertificate(), license.getSignature());
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new ServiceException("授权密钥生成失败", e);
        }
    }

    private String sign(String payload, String privateKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(decodePrivateKey(privateKey));
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new ServiceException("授权证书签名失败", e);
        }
    }

    private PrivateKey decodePrivateKey(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            throw new ServiceException("根证书私钥解析失败", e);
        }
    }

    private String encode(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
