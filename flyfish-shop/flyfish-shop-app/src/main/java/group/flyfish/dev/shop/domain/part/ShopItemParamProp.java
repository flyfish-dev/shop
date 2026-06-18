package group.flyfish.dev.shop.domain.part;

import group.flyfish.dev.annotations.data.Property;
import lombok.Data;

/**
 * 商品参数项
 * 用于生成商品表单
 *
 * @author wangyu
 */
@Data
public class ShopItemParamProp {

    @Property("参数名")
    private String name;

    @Property("是否必填")
    private Boolean required;

    @Property("输入提示")
    private String placeholder;

    @Property(value = "候选项", description = "支持冒号标记键值")
    private String candidates;

    @Property("输入形式")
    private InputMode inputMode;

    /**
     * 输入模式
     */
    public enum InputMode {

        TEXT, NUMBER, RADIO, CHECKBOX, SELECT
    }
}
