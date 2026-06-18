package group.flyfish.dev.shop.domain.vo;

import lombok.Data;

/**
 * 订单交付文件下载内容。
 * <p>该对象只在服务层到控制器之间流转，不进入普通 JSON 响应。</p>
 */
@Data
public class ShopOrderDeliveryDownloadVo {

    /**
     * 下载文件名。
     */
    private String name;

    /**
     * MIME 类型。
     */
    private String contentType;

    /**
     * 文件字节内容。
     */
    private byte[] content;

    /**
     * 文件大小，单位字节。
     */
    private Long size;
}
