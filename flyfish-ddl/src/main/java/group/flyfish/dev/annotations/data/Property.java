package group.flyfish.dev.annotations.data;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * bean的属性注解，提供描述
 *
 * @author wangyu
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Properties.class)
public @interface Property {

    /**
     * 手动指定key，用于覆盖模式
     *
     * @return 结果
     */
    String key() default "";

    /**
     * 显示标题
     *
     * @return 结果
     */
    @AliasFor("title")
    String value() default "";

    /**
     * 显示标题（别名）
     *
     * @return 结果
     */
    @AliasFor("value")
    String title() default "";

    /**
     * 描述
     *
     * @return 结果
     */
    String description() default "";

    /**
     * 被继承的，用于父类，自动拼接名称
     *
     * @return 结果
     */
    boolean inherited() default false;

    /**
     * 是否只读
     *
     * @return 结果
     */
    boolean readonly() default false;

    /**
     * 排序
     *
     * @return 结果
     */
    int order() default 0;

    /**
     * 分组
     *
     * @return 结果
     */
    String group() default "";
}
