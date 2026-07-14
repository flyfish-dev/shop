package group.flyfish.dev.shop.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 商品多语言维护内容。
 *
 * <p>主语言仍然使用 {@link ShopItemCreateDto} / {@link ShopItemUpdateDto}
 * 上的标准字段；这里仅保存额外语言的覆盖内容，缺省时前端会自动回退主语言。</p>
 */
@Data
public class ShopItemI18nDto {

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
