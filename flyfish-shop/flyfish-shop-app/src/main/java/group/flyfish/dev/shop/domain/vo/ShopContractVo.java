package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合同展示数据。
 */
@Data
public class ShopContractVo {

    private Long id;

    private String name;

    private String type;

    private String typeName;

    private String description;

    private List<String> tags;

    private Boolean enabled;

    private Integer sort;

    private List<ShopContractFileVo> files;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
