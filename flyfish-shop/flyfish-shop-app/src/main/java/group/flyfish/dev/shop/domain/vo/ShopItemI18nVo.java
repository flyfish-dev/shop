package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品多语言展示内容。
 */
@Data
public class ShopItemI18nVo {

    /**
     * 商品名称。
     */
    private String name;

    /**
     * 商品标签。
     */
    private List<String> tags;

    /**
     * 商品图文描述，支持 Markdown。
     */
    private String description;
}
