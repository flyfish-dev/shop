package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

/**
 * 订单交付文件摘要。
 * <p>这里只返回文件元数据和受登录态保护的文件编码，避免把授权正文直接散落在页面 JSON 中。</p>
 */
@Data
public class ShopOrderDeliveryFileVo {

    /**
     * 文件编码，前端下载时传回后端。
     */
    private String code;

    /**
     * 下载文件名。
     */
    private String name;

    /**
     * 文件说明。
     */
    private String description;

    /**
     * MIME 类型。
     */
    private String contentType;

    /**
     * 文件大小，单位字节。
     */
    private Long size;
}
