package group.flyfish.dev.shop.converter.impl;

import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.shop.converter.ShopItemParamValue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * 数字商品交付参数。
 * <p>商品只保存可提取内容的模板；订单支付成功后会复制一份快照到订单交付表，避免后续修改商品影响历史订单。</p>
 */
@Data
public class DigitalDeliveryParamValue implements ShopItemParamValue {

    /**
     * 用户在订单页看到的提货标题。
     */
    private String title;

    /**
     * 数字商品提货正文，支持链接、账号说明、Markdown 等文本内容。
     */
    private String content;

    /**
     * 数字商品附件，例如压缩包、授权文件或说明文档。
     */
    private List<FileAttachmentVo> attachments;

    public void normalize() {
        title = StringUtils.defaultIfBlank(StringUtils.trimToNull(title), "数字商品提货内容");
        content = StringUtils.trimToNull(content);
        attachments = attachments == null ? List.of() : attachments.stream()
                .filter(attachment -> attachment != null && StringUtils.isNotBlank(attachment.getUrl()))
                .limit(8)
                .toList();
    }

    public boolean hasDeliveryContent() {
        return StringUtils.isNotBlank(content) || (attachments != null && !attachments.isEmpty());
    }
}
