package group.flyfish.dev.generator.utils;

import group.flyfish.dev.common.bean.data.SimpleKeyValue;
import group.flyfish.dev.utils.type.CastUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ContentUtils {

    private static final List<String> ITEM_SPLITTER = Arrays.asList(" ", "，", ",");

    private static final List<String> PAIR_SPLITTER = Arrays.asList(":", "：", "-");

    /**
     * 截取取得注释第一个词
     *
     * @param comment 注释
     * @return 结果
     */
    public static String simpleComment(String comment) {
        comment = StringUtils.trim(comment);
        if (StringUtils.isBlank(comment)) {
            return "";
        }
        if (comment.contains("（")) {
            return StringUtils.substringBefore(comment, "（").trim();
        }
        if (comment.contains(" ")) {
            return StringUtils.substringBefore(comment, " ").trim();
        }
        return comment;
    }

    /**
     * 判断是否是大字段
     *
     * @param dbType 数据库类型
     * @return 结果
     */
    public static boolean isBlob(String dbType) {
        return dbType.endsWith("text") || dbType.endsWith("binary") || dbType.endsWith("blob") ||
                dbType.equals("json");
    }

    /**
     * 解析注释，取得下拉列表
     *
     * @param comment 评价
     * @return 结果
     */
    public static List<SimpleKeyValue> parseComment(String comment) {
        // 取得中文括号内容
        String content = StringUtils.substringBetween(comment, "（", "）");
        if (StringUtils.isBlank(content)) {
            return Collections.emptyList();
        }
        return ITEM_SPLITTER.stream().map(content::split)
                .filter(parsed -> parsed.length > 1)
                .findFirst()
                .map(contents -> Arrays.stream(contents).map(pair -> {
                                    String[] pairs = splitPair(pair);
                                    if (pairs.length > 1) {
                                        return new SimpleKeyValue(pairs[0], pairs[1]);
                                    }
                                    return null;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                )
                .orElse(null);
    }

    private static String[] splitPair(String pairs) {
        return PAIR_SPLITTER.stream().map(pairs::split)
                .filter(parsed -> parsed.length > 1)
                .findFirst()
                .orElse(new String[]{});
    }

    /**
     * 判断某个字段是否是枚举
     *
     * @param context 上下文
     * @param key     键
     * @return 结果
     */
    public static boolean supportOptions(Map<String, Object> context, String key) {
        List<Map<String, Object>> options = CastUtils.cast(context.get("options"));
        if (CollectionUtils.isNotEmpty(options)) {
            return options.stream().anyMatch(map -> key.equals(map.get("name")));
        }
        return false;
    }
}
