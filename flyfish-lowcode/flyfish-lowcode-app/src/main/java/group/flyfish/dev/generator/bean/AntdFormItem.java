package group.flyfish.dev.generator.bean;

import group.flyfish.dev.generator.utils.ContentUtils;
import group.flyfish.dev.utils.builder.map.MapBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 表单项属性
 *
 * @author wangyu
 */
@Data
public class AntdFormItem {

    private static final Map<Predicate<TableFieldWrapper>, FormItemProps> COMPONENTS = MapBuilder.create(new LinkedHashMap<Predicate<TableFieldWrapper>, FormItemProps>())
            .put(field -> field.getType().equals("tinyint(1)"), FormItemProps.of("a-switch", "请设置", ""))
            .put(field -> field.getType().endsWith("text") || field.getPropertyName().startsWith("desc") || field.getPropertyName().equals("comment"),
                    FormItemProps.of("a-textarea", "请输入", "rows=3,placeholder"))
            .put(field -> field.getType().equals("date"), FormItemProps.of("date-picker", "请选择", "type='date',placeholder"))
            .put(field -> field.getType().equals("datetime"), FormItemProps.of("date-picker", "请选择", "type='datetime',placeholder"))
            .put(field -> field.getType().equals("json"), FormItemProps.of("object-input", "请设置", "type='list'"))
            .put(field -> field.getType().contains("int") || field.getType().contains("long"),
                    FormItemProps.of("a-input-number", "请输入", "min=0,max=9999,placeholder"))
            .put(field -> field.getType().startsWith("decimal"),
                    FormItemProps.of("a-input-number", "请输入", "min=0,max=9999,step=1,precision=2,placeholder"))
            .put(field -> StringUtils.defaultString(field.getComment()).contains("（") && ContentUtils.supportOptions(field.context, field.getPropertyName()),
                    FormItemProps.of("a-select", "请选择", "options,placeholder"))
            .put(field -> true, FormItemProps.of("a-input", "请输入", "placeholder"))
            .build();

    private String code;

    private String title;

    private String dbType;

    private Boolean required;

    private String action;

    private List<List<String>> props;

    private String component;

    public AntdFormItem(GeneratedField field, Map<String, Object> context) {
        this.code = field.getPropertyName();
        this.title = ContentUtils.simpleComment(field.getComment());
        this.dbType = field.getType();
        this.required = !field.getMetaInfo().isNullable();
        this.buildProps(field, context);
    }

    private void buildProps(GeneratedField field, Map<String, Object> context) {
        COMPONENTS.keySet().stream().
                filter(key -> key.test(TableFieldWrapper.wrap(field, context)))
                .findFirst()
                .ifPresent(key -> {
                    FormItemProps result = COMPONENTS.get(key);
                    this.component = result.component;
                    this.action = result.action;
                    this.props = this.extractProps(result, field);
                });
    }

    public List<List<String>> extractProps(FormItemProps item, GeneratedField field) {
        return Arrays.stream(item.props.split(","))
                .filter(StringUtils::isNotBlank)
                .map(pair -> {
                    List<String> pairs = Arrays.asList(pair.split("="));
                    List<String> prop = new ArrayList<>();
                    String key = pairs.get(0);
                    String value = pairs.size() > 1 ? pairs.get(1) : "";
                    prop.add(key);
                    if (StringUtils.isNotBlank(value)) {
                        prop.add(value);
                    } else {
                        if (key.equals("placeholder")) {
                            prop.add("'" + item.action + title + "'");
                        } else if (key.equals("options")) {
                            prop.add("options." + field.getPropertyName());
                        } else {
                            throw new RuntimeException("未知的属性！");
                        }
                    }
                    return prop;
                })
                .collect(Collectors.toList());
    }

    @AllArgsConstructor(staticName = "of")
    public static class FormItemProps {

        private String component;

        private String action;

        private String props;
    }

    @AllArgsConstructor(staticName = "wrap")
    public static class TableFieldWrapper {

        @Delegate
        private GeneratedField field;

        private Map<String, Object> context;
    }
}
