package group.flyfish.dev.shop.license;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemSku;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopItemSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 商业版和企业版首购维护协议服务。
 *
 * <p>首年维护权益已经包含在商品价格中，本服务只解析是否需要签署协议，
 * 不参与金额计算。客户端只能表达是否确认，合同 ID 始终由服务端商品配置决定。</p>
 */
@Service
@RequiredArgsConstructor
public class LicenseMaintenanceAgreementService {

    private final ShopItemRepository itemRepository;
    private final ShopItemSkuRepository skuRepository;

    public Mono<Selection> resolve(Long itemId, Long skuId, boolean accepted) {
        return itemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> {
                    if (skuId == null) {
                        return Mono.just(resolve(item, accepted));
                    }
                    return skuRepository.findById(skuId)
                            .filter(sku -> itemId.equals(sku.getItemId()))
                            .switchIfEmpty(Mono.error(new BusinessException("SKU_NOT_FOUND", "SKU不存在")))
                            .map(sku -> resolve(item, sku, accepted));
                });
    }

    public Selection resolve(ShopItem effectiveItem, boolean accepted) {
        if (effectiveItem == null) {
            if (accepted) {
                throw new BusinessException("LICENSE_MAINTENANCE_AGREEMENT_UNAVAILABLE",
                        "该商品无需确认年度维护协议");
            }
            return Selection.unavailable();
        }
        LicenseDeliveryParamValue license =
                ShopItemParamValue.licenseDeliveryConfiguration(effectiveItem.getParams());
        return resolve(license, accepted);
    }

    private Selection resolve(ShopItem item, ShopItemSku sku, boolean accepted) {
        ShopItem effective = new ShopItem();
        effective.setName(item.getName() + " - " + sku.getName());
        effective.setType(sku.getType());
        effective.setParams(sku.getParams());
        return resolve(effective, accepted);
    }

    private Selection resolve(LicenseDeliveryParamValue license, boolean accepted) {
        LicenseDeliveryParamValue.MaintenanceAgreement config = license.getMaintenanceAgreement();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            if (accepted) {
                throw new BusinessException("LICENSE_MAINTENANCE_AGREEMENT_UNAVAILABLE", "该授权无需确认年度维护协议");
            }
            return Selection.unavailable();
        }
        if (config.getContractIds() == null || config.getContractIds().isEmpty()) {
            throw new BusinessException("LICENSE_MAINTENANCE_CONTRACT_MISSING", "年度服务协议尚未配置");
        }
        return new Selection(true, Boolean.TRUE.equals(config.getRequired()), accepted, config.getContractIds());
    }

    public record Selection(boolean available, boolean required, boolean accepted, List<Long> contractIds) {

        private static Selection unavailable() {
            return new Selection(false, false, false, List.of());
        }

        public List<Long> acceptedContractIds() {
            return accepted ? contractIds : List.of();
        }

        public void validateAccepted() {
            if (available && required && !accepted) {
                throw new BusinessException("LICENSE_MAINTENANCE_AGREEMENT_REQUIRED", "请先确认并签署年度维护协议");
            }
        }
    }
}
