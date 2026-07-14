package group.flyfish.dev.annotations.data;

import java.lang.annotation.*;

/**
 * 加在类上，指定父类的一些属性，优先级更高
 *
 * @author wangyu
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Properties {

    /**
     * 属性们
     *
     * @return 结果
     */
    Property[] value();
}
