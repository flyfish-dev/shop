package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 合同附加语言的展示内容。
 */
@Data
public class ShopContractI18nVo {

    private String name;

    private String description;

    private List<String> tags;
}
