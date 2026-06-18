package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品购买前需要阅读的合同。
 */
@Data
public class ShopContractAgreementVo {

    private Long id;

    private String name;

    private String type;

    private String typeName;

    private String description;

    private List<String> tags;

    private List<ShopContractFileVo> files;
}
