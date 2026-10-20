package group.flyfish.dev.shop.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 合同附加语言的维护内容。
 */
@Data
public class ShopContractI18nDto {

    private String name;

    private String description;

    private List<String> tags;
}
