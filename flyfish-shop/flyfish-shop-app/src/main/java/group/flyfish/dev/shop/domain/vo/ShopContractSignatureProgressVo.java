package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

/**
 * 合同签署进度。
 */
@Data
public class ShopContractSignatureProgressVo {

    private String signToken;

    private int totalCount;

    private int agreedCount;

    private boolean completed;
}
