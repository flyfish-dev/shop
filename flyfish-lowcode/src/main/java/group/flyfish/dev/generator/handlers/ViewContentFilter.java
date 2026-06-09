package group.flyfish.dev.generator.handlers;

import group.flyfish.dev.common.bean.data.SimpleKeyValue;
import group.flyfish.dev.generator.bean.AntdFormItem;
import group.flyfish.dev.generator.bean.GeneratedTable;
import group.flyfish.dev.generator.utils.ContentUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.atteo.evo.inflector.English;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 视图内容过滤器
 *
 * @author wangyu
 * 该类主要负责处理视图需要的数据
 */
@Slf4j
public class ViewContentFilter {

    /**
     * 内容过滤
     *
     * @param tableInfo 表信息
     * @param context   上下文
     */
    public void filter(GeneratedTable tableInfo, Map<String, Object> context) {
        String mapping = (String) context.get("controllerMappingHyphen");
        // 英文复数形式转换
        if (StringUtils.isNotBlank(mapping)) {
            String cleared = StringUtils.substringAfter(mapping, "app-");
            String last = cleared.contains("-") ? StringUtils.substringAfterLast(cleared, "-") : cleared;
            context.put("formCode", cleared);
            context.put("controllerMappingHyphen", cleared.replace(last, English.plural(last)));
        }
        // 提取列表展示内容，过滤大字段
        List<SimpleKeyValue> columns = tableInfo.getFields().stream()
                .filter(column -> !ContentUtils.isBlob(column.getType()))
                .map(column -> new SimpleKeyValue(column.getPropertyName(), ContentUtils.simpleComment(column.getComment())))
                .collect(Collectors.toList());
        context.put("columns", columns);
        // 构建静态枚举
        List<Map<String, Object>> options = tableInfo.getFields().stream()
                .map(field -> {
                    List<SimpleKeyValue> values = ContentUtils.parseComment(field.getComment());
                    if (CollectionUtils.isNotEmpty(values)) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", field.getPropertyName());
                        map.put("list", values);
                        return map;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        context.put("options", options);
        // 构建表单对象，修正表单基础属性
        List<AntdFormItem> forms = tableInfo.getFields().stream()
                .map(field -> new AntdFormItem(field, context))
                .collect(Collectors.toList());
        context.put("forms", forms);
    }
}
