package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

/**
 * 合同文件展示数据。
 */
@Data
public class ShopContractFileVo {

    private Long id;

    private Long contractId;

    private String documentKey;

    private String locale;

    private String fileName;

    private String fileUrl;

    private String fileHash;

    private Integer fileVersion;

    private String contentType;

    private Long fileSize;

    private Integer sort;

    private Boolean enabled;
}
