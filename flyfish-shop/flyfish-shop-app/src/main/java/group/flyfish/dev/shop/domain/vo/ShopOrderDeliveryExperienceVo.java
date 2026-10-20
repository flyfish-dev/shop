package group.flyfish.dev.shop.domain.vo;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.shop.converter.impl.DeliveryExperienceParamValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 订单交付后的运行体验指引。
 */
@Data
public class ShopOrderDeliveryExperienceVo {

    private String title;

    private String titleEn;

    private String description;

    private String descriptionEn;

    private List<Link> links;

    public static ShopOrderDeliveryExperienceVo from(String metadata) {
        if (StringUtils.isBlank(metadata)) {
            return null;
        }
        try {
            DeliveryExperienceParamValue value = JacksonUtils.readValue(
                    metadata, DeliveryExperienceParamValue.class);
            value.normalize();
            if (!value.hasLinks()) {
                return null;
            }
            ShopOrderDeliveryExperienceVo vo = new ShopOrderDeliveryExperienceVo();
            vo.setTitle(value.getTitle());
            vo.setTitleEn(value.getTitleEn());
            vo.setDescription(value.getDescription());
            vo.setDescriptionEn(value.getDescriptionEn());
            vo.setLinks(value.getLinks().stream().map(Link::new).toList());
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    @Data
    public static class Link {

        private String title;

        private String titleEn;

        private String description;

        private String descriptionEn;

        private String url;

        private String kind;

        private boolean primary;

        public Link() {
        }

        public Link(DeliveryExperienceParamValue.Link source) {
            title = source.getTitle();
            titleEn = source.getTitleEn();
            description = source.getDescription();
            descriptionEn = source.getDescriptionEn();
            url = source.getUrl();
            kind = source.getKind();
            primary = source.isPrimary();
        }
    }
}
