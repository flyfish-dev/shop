package group.flyfish.dev.shop.domain.po;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.common.base.reactive.AuditDomain;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 商品查看统计。
 *
 * <p>查看次数属于高频写入数据，单独建表避免频繁更新商品主表。</p>
 */
@Getter
@Setter
@Accessors(chain = false)
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
@Table("shop_item_view_stat")
public class ShopItemViewStat extends AuditDomain {

    @Property("商品id")
    @Column("item_id")
    private Long itemId;

    @Property("累计查看次数")
    @Column("view_count")
    private Long viewCount;

    @Property("最近查看时间")
    @Column("last_view_time")
    private LocalDateTime lastViewTime;
}
