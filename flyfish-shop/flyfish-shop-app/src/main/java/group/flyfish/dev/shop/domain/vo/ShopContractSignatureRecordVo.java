package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合同签署留痕。
 */
@Data
public class ShopContractSignatureRecordVo {

    private Long id;

    private String signToken;

    private String orderNo;

    private Long itemId;

    private Long buyerId;

    private Long contractId;

    private Long contractFileId;

    private String contractName;

    private String contractType;

    private String fileName;

    private String fileUrl;

    private Integer readPercent;

    private String status;

    private LocalDateTime agreedTime;

    private String clientIp;

    private String userAgent;

    private LocalDateTime createTime;
}
