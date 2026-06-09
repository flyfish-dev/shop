package group.flyfish.dev.shop.domain.qo;

import group.flyfish.dev.common.base.reactive.BaseQo;
import group.flyfish.dev.shop.domain.po.ShopItemGroup;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.relational.core.query.Criteria;

import java.util.List;

/**
 * 商品分组查询实体
 *
 * @author wangyu
 */
@Data
public class ShopItemGroupListQo extends BaseQo<ShopItemGroup> {

    private List<Long> ids;

    private Long shopId;

    @Override
    public Criteria getCriteria() {
        Criteria criteria = Criteria.empty();
        if (CollectionUtils.isNotEmpty(ids)) {
            criteria = criteria.and("id").in(ids);
        }
        if (null != shopId) {
            criteria = criteria.and("shop_id").is(shopId);
        }
        return criteria;
    }
}
