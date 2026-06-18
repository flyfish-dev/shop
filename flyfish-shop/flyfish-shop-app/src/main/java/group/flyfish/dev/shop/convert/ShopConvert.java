package group.flyfish.dev.shop.convert;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.ShopItemDeliveryPlan;
import group.flyfish.dev.shop.domain.dto.*;
import group.flyfish.dev.shop.domain.part.ShopItemParamProp;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.domain.po.ShopItem;
import group.flyfish.dev.shop.domain.po.ShopItemGroup;
import group.flyfish.dev.shop.domain.vo.ShopItemDetailVo;
import group.flyfish.dev.shop.domain.vo.ShopItemGroupListVo;
import group.flyfish.dev.shop.domain.vo.ShopItemListVo;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;

/**
 * 店铺相关实体转换器
 *
 * @author wangyu
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface ShopConvert {

    // ===================== Shop 相关转换 =====================

    /**
     * Shop创建DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    Shop convert(ShopCreateDto dto);

    /**
     * Shop更新DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    void update(@MappingTarget Shop shop, ShopUpdateDto dto);

    // ===================== ShopItemGroup 相关转换 =====================

    /**
     * 商品分组转列表VO
     */
    ShopItemGroupListVo convert(ShopItemGroup group);

    /**
     * 商品分组创建DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shopId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    ShopItemGroup convert(ShopItemGroupCreateDto dto);

    /**
     * 商品分组更新DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shopId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    void update(@MappingTarget ShopItemGroup group, ShopItemGroupUpdateDto dto);

    // ===================== ShopItem 相关转换 =====================

    /**
     * 商品转列表VO
     */
    @Mapping(target = "tags", source = "tags", qualifiedByName = "toListString")
    @Mapping(target = "type", expression = "java(item.getType() == null ? null : item.getType().name())")
    @Mapping(target = "typeName", expression = "java(toItemTypeName(item.getType()))")
    @Mapping(target = "deliveryMode", expression = "java(toDeliveryMode(item).name())")
    @Mapping(target = "deliveryModeName", expression = "java(toDeliveryModeName(toDeliveryMode(item)))")
    @Mapping(target = "deliveryActions", expression = "java(toDeliveryActions(item))")
    @Mapping(target = "defaultCouponPreview", ignore = true)
    @Mapping(target = "contractRequired", ignore = true)
    ShopItemListVo toItemList(ShopItem item);

    /**
     * 商品转详情VO
     */
    @Mapping(target = "tags", source = "tags", qualifiedByName = "toListString")
    @Mapping(target = "images", source = "images", qualifiedByName = "toListString")
    @Mapping(target = "type", expression = "java(item.getType() == null ? null : item.getType().name())")
    @Mapping(target = "typeName", expression = "java(toItemTypeName(item.getType()))")
    @Mapping(target = "deliveryMode", expression = "java(toDeliveryMode(item).name())")
    @Mapping(target = "deliveryModeName", expression = "java(toDeliveryModeName(toDeliveryMode(item)))")
    @Mapping(target = "deliveryActions", expression = "java(toDeliveryActions(item))")
    @Mapping(target = "defaultCouponPreview", ignore = true)
    @Mapping(target = "contractRequired", ignore = true)
    @Mapping(target = "contractIds", ignore = true)
    ShopItemDetailVo toItemDetail(ShopItem item);

    /**
     * 商品创建DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shopId", ignore = true)
    @Mapping(target = "buyCount", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "fromListString")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "fromListString")
    ShopItem convert(ShopItemCreateDto dto);

    /**
     * 商品更新DTO转PO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "shopId", ignore = true)
    @Mapping(target = "buyCount", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delete", ignore = true)
    @Mapping(target = "images", source = "images", qualifiedByName = "fromListString")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "fromListString")
    void update(@MappingTarget ShopItem item, ShopItemUpdateDto dto);

    // ===================== 通用转换方法 =====================

    @Named("toListString")
    default List<String> toListString(String value) {
        return Optional.ofNullable(value)
                .filter(StringUtils::isNotBlank)
                .map(v -> v.split(","))
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    @Named("fromListString")
    default String fromListString(List<String> list) {
        return Optional.ofNullable(list)
                .filter(l -> !l.isEmpty())
                .map(l -> l.stream()
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.joining(",")))
                .orElse(null);
    }

    default String toItemTypeName(ShopItem.Type type) {
        if (type == null) {
            return "普通商品";
        }
        return switch (type) {
            case GIT_REPOSITORY_ACCESS, GIT_REPOSITORY_DONATION_ACCESS,
                    DIGITAL_DOWNLOAD, SERVICE_PACKAGE, LICENSE -> type.getTitle();
        };
    }

    default ShopItem.DeliveryMode toDeliveryMode(ShopItem item) {
        if (item == null) {
            return ShopItem.DeliveryMode.MANUAL;
        }
        if (item.getType() != null) {
            return item.getType().normalizeDeliveryMode(item.getDeliveryMode());
        }
        if (item.getDeliveryMode() != null) {
            return item.getDeliveryMode();
        }
        return ShopItem.DeliveryMode.MANUAL;
    }

    default String toDeliveryModeName(ShopItem.DeliveryMode deliveryMode) {
        return deliveryMode == null ? ShopItem.DeliveryMode.MANUAL.getTitle() : deliveryMode.getTitle();
    }

    default List<String> toDeliveryActions(ShopItem item) {
        return ShopItemDeliveryPlan.actionNames(item);
    }

    @Named("toParamsList")
    default List<ShopItemParamProp> toParamsList(String params) {
        if (StringUtils.isNotBlank(params)) {
            return JacksonUtils.readValue(params, new TypeReference<>() {
            });
        }
        return Collections.emptyList();
    }
}
