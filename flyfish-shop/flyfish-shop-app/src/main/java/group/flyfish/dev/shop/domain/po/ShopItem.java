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

import java.math.BigDecimal;
import java.util.Set;

/**
 * 店铺商品
 *
 * @author wangyu
 */
@Getter
@Setter
@Accessors(chain = false)
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @PersistenceCreator)
@Table("shop_item")
public class ShopItem extends AuditDomain {

    @Property("商品名称")
    private String name;

    @Property("商品封面")
    private String cover;

    @Property("商品图集")
    private String images;

    @Property("商品价格")
    private BigDecimal price;

    @Property("商品分组id")
    @Column("group_id")
    private Long groupId;

    @Property("所属店铺id")
    @Column("shop_id")
    private Long shopId;

    @Property("商品类型")
    private Type type;

    @Property("交付方式")
    @Column("delivery_mode")
    private DeliveryMode deliveryMode;

    @Property("商品标签")
    private String tags;

    @Property("参数")
    private String params;

    @Property("购买人数")
    @Column("buy_count")
    private Integer buyCount;

    @Property("商品描述")
    private String description;

    @Property("排序")
    private Integer sort;

    @Property("上架状态")
    private Boolean enabled;

    @Property("置顶状态")
    private Boolean pinned;

    @Property("推荐状态")
    private Boolean recommended;

    @Property("醒目样式")
    @Column("highlight_style")
    private String highlightStyle;

    @Property("醒目图标")
    @Column("highlight_icon")
    private String highlightIcon;

    @Property("启用默认优惠券")
    @Column("default_coupon_enabled")
    private Boolean defaultCouponEnabled;

    @Property("默认优惠券编码")
    @Column("default_coupon_code")
    private String defaultCouponCode;

    @Getter
    public enum Type {

        GIT_REPOSITORY_ACCESS("Git 仓库开通", DeliveryMode.AUTOMATIC, Set.of(DeliveryMode.AUTOMATIC)),
        GIT_REPOSITORY_DONATION_ACCESS("Git 仓库打赏开通", DeliveryMode.AUTOMATIC, Set.of(DeliveryMode.AUTOMATIC)),

        DIGITAL_DOWNLOAD("数字下载", DeliveryMode.AUTOMATIC,
                Set.of(DeliveryMode.AUTOMATIC, DeliveryMode.MANUAL, DeliveryMode.NONE)),
        SERVICE_PACKAGE("服务套餐", DeliveryMode.MANUAL, Set.of(DeliveryMode.MANUAL, DeliveryMode.NONE)),
        LICENSE("授权许可", DeliveryMode.AUTOMATIC,
                Set.of(DeliveryMode.AUTOMATIC, DeliveryMode.MANUAL, DeliveryMode.NONE));

        private final String title;

        private final DeliveryMode defaultDeliveryMode;

        private final Set<DeliveryMode> deliveryModes;

        Type(String title, DeliveryMode defaultDeliveryMode, Set<DeliveryMode> deliveryModes) {
            this.title = title;
            this.defaultDeliveryMode = defaultDeliveryMode;
            this.deliveryModes = deliveryModes;
        }

        public boolean supportsDeliveryMode(DeliveryMode deliveryMode) {
            return deliveryMode != null && deliveryModes.contains(deliveryMode);
        }

        public DeliveryMode normalizeDeliveryMode(DeliveryMode deliveryMode) {
            return supportsDeliveryMode(deliveryMode) ? deliveryMode : defaultDeliveryMode;
        }

        public boolean requiresAutomaticDelivery() {
            return defaultDeliveryMode == DeliveryMode.AUTOMATIC
                    && deliveryModes.size() == 1
                    && deliveryModes.contains(DeliveryMode.AUTOMATIC);
        }

        public boolean usesGitRepositoryAccessParams() {
            return this == GIT_REPOSITORY_ACCESS || this == GIT_REPOSITORY_DONATION_ACCESS;
        }
    }

    @Getter
    public enum DeliveryMode {

        AUTOMATIC("自动交付"),
        MANUAL("人工交付"),
        NONE("无需交付");

        private final String title;

        DeliveryMode(String title) {
            this.title = title;
        }
    }
}
