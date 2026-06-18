package group.flyfish.dev.shop.domain.qo;

import group.flyfish.dev.common.base.reactive.PageableQo;
import group.flyfish.dev.shop.domain.po.ShopItem;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;

import java.util.List;

/**
 * 商品列表查询实体
 *
 * @author wangyu
 */
@Data
public class ShopItemListQo extends PageableQo<ShopItem> {

    // 分组id，可多选
    private List<Long> groupIds;

    // 分组id，兼容管理页单选筛选
    private Long groupId;

    // 模糊匹配的商品名
    private String name;

    // 商品标签
    private String tag;

    // 是否包含已下架商品
    private Boolean includeDisabled = false;

    // 是否只查询置顶商品
    private Boolean pinned;

    // 是否只查询推荐商品
    private Boolean recommended;

    // 排序类型
    private Order order;

    public enum Order {

        PRICE_DESC, PRICE_ASC, COUNT_DESC, COUNT_ASC
    }

    @Override
    public Sort sorts() {
        Sort sort = Sort.by(Sort.Order.desc("pinned"));
        if (null == order) {
            return sort.and(Sort.by(
                    Sort.Order.asc("sort"),
                    Sort.Order.desc("recommended"),
                    Sort.Order.desc("update_time"),
                    Sort.Order.desc("id")
            ));
        }
        Sort.Order result = switch (order) {
            case COUNT_ASC -> Sort.Order.asc("buy_count");
            case PRICE_ASC -> Sort.Order.asc("price");
            case COUNT_DESC -> Sort.Order.desc("buy_count");
            case PRICE_DESC -> Sort.Order.desc("price");
        };
        return sort.and(Sort.by(result, Sort.Order.asc("sort"), Sort.Order.desc("id")));
    }

    @Override
    public Criteria getCriteria() {
        Criteria criteria = Criteria.empty();
        if (CollectionUtils.isNotEmpty(groupIds)) {
            criteria = criteria.and("group_id").in(groupIds);
        }
        if (null != groupId) {
            criteria = criteria.and("group_id").is(groupId);
        }
        if (StringUtils.isNotEmpty(name)) {
            criteria = criteria.and("name").like("%" + name + "%");
        }
        if (StringUtils.isNotEmpty(tag)) {
            criteria = criteria.and("tags").like("%" + tag + "%");
        }
        if (pinned != null) {
            criteria = criteria.and("pinned").is(pinned);
        }
        if (recommended != null) {
            criteria = criteria.and("recommended").is(recommended);
        }
        if (!Boolean.TRUE.equals(includeDisabled)) {
            criteria = criteria.and("enabled").is(true);
        }
        return criteria;
    }
}
