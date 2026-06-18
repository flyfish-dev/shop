package group.flyfish.dev.shop.service.impl;

import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.convert.ShopConvert;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import group.flyfish.dev.shop.converter.impl.DigitalDeliveryParamValue;
import group.flyfish.dev.shop.converter.impl.GitRepositoryAccessParamValue;
import group.flyfish.dev.shop.converter.impl.LicenseDeliveryParamValue;
import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.shop.domain.po.ShopDeliveryAction;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemGroup;
import group.flyfish.dev.shop.repository.ShopItemGroupRepository;
import group.flyfish.dev.shop.repository.ShopItemRepository;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.shop.service.ShopContractService;
import group.flyfish.dev.shop.service.ShopManageService;
import group.flyfish.dev.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 店铺管理服务实现类
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class ShopManageServiceImpl implements ShopManageService {

    private final ShopRepository shopRepository;
    private final ShopItemRepository shopItemRepository;
    private final ShopItemGroupRepository shopItemGroupRepository;
    private final ShopService shopService;
    private final ShopContractService shopContractService;
    private final ShopConvert shopConvert;

    @Override
    @Transactional
    public Mono<Shop> createShop(ShopCreateDto dto) {
        return Mono.just(dto)
                .map(shopConvert::convert)
                .flatMap(shopRepository::save);
    }

    @Override
    @Transactional
    public Mono<Shop> updateShop(Long id, ShopUpdateDto dto) {
        return shopRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "店铺不存在")))
                .doOnNext(shop -> shopConvert.update(shop, dto))
                .flatMap(shopRepository::save);
    }

    @Override
    @Transactional
    public Mono<Void> deleteShop(Long id) {
        return shopRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "店铺不存在")))
                .flatMap(shop -> shopRepository.deleteById(shop.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> createItemGroup(ShopItemGroupCreateDto dto) {
        return requireSavedShop()
                .map(shop -> {
                    ShopItemGroup group = shopConvert.convert(dto);
                    group.setShopId(shop.getId());
                    if (group.getSort() == null) {
                        group.setSort(0);
                    }
                    if (group.getEnabled() == null) {
                        group.setEnabled(true);
                    }
                    return group;
                })
                .flatMap(shopItemGroupRepository::save)
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> updateItemGroup(Long id, ShopItemGroupUpdateDto dto) {
        return shopItemGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                .doOnNext(group -> shopConvert.update(group, dto))
                .flatMap(shopItemGroupRepository::save)
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> deleteItemGroup(Long id) {
        return shopItemGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                .flatMap(group -> shopItemGroupRepository.deleteById(group.getId()));
    }

    @Override
    @Transactional
    public Mono<Void> createItem(ShopItemCreateDto dto) {
        return validateItemCreate(dto)
                .map(shop -> {
                    ShopItem item = shopConvert.convert(dto);
                    item.setShopId(shop.getId());
                    item.setBuyCount(0);
                    normalizeDeliveryMode(item, dto.getDeliveryMode() != null);
                    normalizeItemParams(item, dto.getDeliveryActions());
                    if (item.getSort() == null) {
                        item.setSort(0);
                    }
                    if (item.getEnabled() == null) {
                        item.setEnabled(false);
                    }
                    if (item.getPinned() == null) {
                        item.setPinned(false);
                    }
                    if (item.getRecommended() == null) {
                        item.setRecommended(false);
                    }
                    normalizeItemPresentation(item);
                    normalizeItemCoupon(item);
                    return item;
                })
                .flatMap(shopItemRepository::save)
                .flatMap(item -> shopContractService.updateItemContracts(item.getId(), dto.getContractIds()))
                .then();
    }

    private Mono<Shop> validateItemCreate(ShopItemCreateDto dto) {
        return Mono.zip(
                requireSavedShop(),
                shopItemGroupRepository.findById(dto.getGroupId())
                        .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
        ).map(tuple -> tuple.getT1());
    }

    private Mono<Shop> requireSavedShop() {
        return shopService.getCurrentShop()
                .filter(shop -> shop.getId() != null)
                .switchIfEmpty(Mono.error(new BusinessException("SHOP_NOT_FOUND", "请先创建店铺")));
    }

    @Override
    @Transactional
    public Mono<Void> updateItem(Long id, ShopItemUpdateDto dto) {
        return shopItemRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> {
                    if (dto.getGroupId() != null) {
                        return shopItemGroupRepository.findById(dto.getGroupId())
                                .switchIfEmpty(Mono.error(new BusinessException("GROUP_NOT_FOUND", "商品分组不存在")))
                                .thenReturn(item);
                    }
                    return Mono.just(item);
                })
                .doOnNext(item -> {
                    shopConvert.update(item, dto);
                    applyExplicitImageUpdate(item, dto);
                    normalizeDeliveryMode(item, dto.getDeliveryMode() != null);
                    normalizeItemParams(item, dto.getDeliveryActions());
                    normalizeItemPresentation(item);
                    normalizeItemCoupon(item);
                })
                .flatMap(shopItemRepository::save)
                .flatMap(item -> dto.getContractIds() == null
                        ? Mono.just(item)
                        : shopContractService.updateItemContracts(item.getId(), dto.getContractIds()).thenReturn(item))
                .then();
    }

    private void applyExplicitImageUpdate(ShopItem item, ShopItemUpdateDto dto) {
        if (dto.getImages() == null) {
            if (dto.getCover() != null) {
                item.setCover(dto.getCover());
            }
            return;
        }
        List<String> images = dto.getImages().stream()
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
        item.setImages(images.isEmpty() ? null : String.join(",", images));
        item.setCover(images.isEmpty() ? null : images.get(0));
    }

    private void normalizeItemParams(ShopItem item, List<ShopDeliveryAction> requestedActions) {
        if (item.getType() == null) {
            return;
        }
        if (item.getType().usesGitRepositoryAccessParams()) {
            GitRepositoryAccessParamValue param = ShopItemParamValue.gitRepositoryAccess(item.getParams());
            param.setDeliveryActions(resolveActions(item, requestedActions, param.getDeliveryActions()));
            if (param.deliversLicense()) {
                LicenseDeliveryParamValue licenseParam = param.getLicenseDelivery() == null
                        ? ShopItemParamValue.licenseDelivery(item.getParams(), item.getName())
                        : param.getLicenseDelivery();
                licenseParam.normalize(item.getName());
                validateLicenseDelivery(item, licenseParam);
                param.setLicenseDelivery(licenseParam);
            } else {
                param.setLicenseDelivery(null);
            }
            if (Boolean.TRUE.equals(item.getEnabled())
                    && param.getDeliveryActions().contains(ShopDeliveryAction.GIT_REPOSITORY_ACCESS)
                    && !param.hasRepository()) {
                throw new BusinessException("GIT_REPOSITORY_REQUIRED", "请输入有效的 Git 仓库地址");
            }
            item.setParams(param.toJSON());
            return;
        }
        if (item.getType() == ShopItem.Type.DIGITAL_DOWNLOAD) {
            DigitalDeliveryParamValue param = ShopItemParamValue.digitalDelivery(item.getParams());
            if (Boolean.TRUE.equals(item.getEnabled())
                    && item.getDeliveryMode() == ShopItem.DeliveryMode.AUTOMATIC
                    && !param.hasDeliveryContent()) {
                throw new BusinessException("DIGITAL_DELIVERY_REQUIRED", "请配置数字商品提货内容");
            }
            item.setParams(param.toJSON());
            return;
        }
        if (item.getType() == ShopItem.Type.LICENSE) {
            LicenseDeliveryParamValue param = ShopItemParamValue.licenseDelivery(item.getParams(), item.getName());
            param.setDeliveryActions(resolveActions(item, requestedActions, param.getDeliveryActions()));
            validateLicenseDelivery(item, param);
            item.setParams(param.toJSON());
        }
    }

    private List<ShopDeliveryAction> resolveActions(ShopItem item, List<ShopDeliveryAction> requestedActions,
                                                    List<ShopDeliveryAction> existingActions) {
        if (requestedActions != null) {
            return ShopItemDeliveryPlan.normalize(requestedActions, item.getType());
        }
        return ShopItemDeliveryPlan.normalize(existingActions, item.getType());
    }

    private void validateLicenseDelivery(ShopItem item, LicenseDeliveryParamValue param) {
        param.normalize(item.getName());
        if (Boolean.TRUE.equals(item.getEnabled())
                && item.getDeliveryMode() == ShopItem.DeliveryMode.AUTOMATIC
                && !param.hasAuthorizedOrigin()) {
            throw new BusinessException("LICENSE_ORIGIN_REQUIRED", "请配置授权部署域名");
        }
        boolean hasWildcard = param.getAllowedOrigins().stream().anyMatch(origin -> origin.contains("*"));
        if (Boolean.TRUE.equals(item.getEnabled()) && !param.isEnterprise()
                && (param.getAllowedOrigins().size() > 1 || hasWildcard || param.getMaxDeployments() > 1)) {
            throw new BusinessException("LICENSE_TIER_INVALID", "个人版和商业版仅允许单域名单部署，企业版才允许多域名或通配域名");
        }
        if (Boolean.TRUE.equals(item.getEnabled())
                && "personal".equals(param.getEdition())
                && Boolean.TRUE.equals(param.getCommercialUse())) {
            throw new BusinessException("LICENSE_PERSONAL_COMMERCIAL_FORBIDDEN", "个人授权禁止商业使用");
        }
    }

    private void normalizeItemPresentation(ShopItem item) {
        item.setHighlightStyle(StringUtils.trimToNull(item.getHighlightStyle()));
        item.setHighlightIcon(StringUtils.trimToNull(item.getHighlightIcon()));
    }

    private void normalizeItemCoupon(ShopItem item) {
        if (!Boolean.TRUE.equals(item.getDefaultCouponEnabled())) {
            item.setDefaultCouponEnabled(false);
            item.setDefaultCouponCode(null);
            return;
        }
        String code = StringUtils.upperCase(StringUtils.trimToNull(item.getDefaultCouponCode()));
        if (code == null) {
            throw new BusinessException("DEFAULT_COUPON_REQUIRED", "请填写默认优惠券编码");
        }
        item.setDefaultCouponCode(code);
    }

    private void normalizeDeliveryMode(ShopItem item, boolean explicitMode) {
        if (item.getType() == null) {
            item.setType(ShopItem.Type.SERVICE_PACKAGE);
        }
        if (item.getType().requiresAutomaticDelivery()) {
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
            return;
        }
        ShopItem.DeliveryMode deliveryMode = item.getDeliveryMode();
        if (deliveryMode == null) {
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
            return;
        }
        if (!item.getType().supportsDeliveryMode(deliveryMode)) {
            if (explicitMode) {
                throw new BusinessException("DELIVERY_MODE_INVALID", "该商品类型不支持当前交付方式");
            }
            item.setDeliveryMode(item.getType().getDefaultDeliveryMode());
        }
    }

    @Override
    @Transactional
    public Mono<Void> deleteItem(Long id) {
        return shopItemRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException("ITEM_NOT_FOUND", "商品不存在")))
                .flatMap(item -> shopItemRepository.deleteById(item.getId()));
    }
}
