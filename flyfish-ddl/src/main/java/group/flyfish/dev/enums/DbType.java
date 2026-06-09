package group.flyfish.dev.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 数据库类型
 * 目前仅兼容主流类型，其他后续迭代
 *
 * @author wangyu
 */
@Getter
public enum DbType {

    VARCHAR("字符串", DbTypeGroup.STRING, CharSequence.class),
    INT("整型", DbTypeGroup.NUMERIC, Integer.class),
    BIGINT("长整型", DbTypeGroup.NUMERIC, Long.class, BigInteger.class),
    DECIMAL("定点型", DbTypeGroup.NUMERIC, BigDecimal.class),
    DOUBLE("浮点型", DbTypeGroup.NUMERIC, Float.class, Double.class),
    TINYINT("枚举型", DbTypeGroup.NUMERIC, Enum.class),
    DATETIME("日期时间", DbTypeGroup.DATE, Date.class, LocalDateTime.class),
    DATE("日期", DbTypeGroup.DATE, LocalDate.class),
    TEXT("长文本", DbTypeGroup.STRING, String.class),
    BIT("布尔型", DbTypeGroup.OTHER, Boolean.class),
    JSON("对象型", DbTypeGroup.OTHER, String.class);

    private final String name;

    private final DbTypeGroup group;

    private final List<Class<?>> candidates;

    DbType(String name, DbTypeGroup group, Class<?>... candidates) {
        this.name = name;
        this.group = group;
        this.candidates = Arrays.asList(candidates);
    }

    public boolean supports(Class<?> type) {
        return this.candidates.stream().anyMatch(candidate -> candidate.isAssignableFrom(type));
    }

    /**
     * 判断某个类型是否属于某个分组
     *
     * @param type  类型字符串
     * @param group 分组
     * @return 是否属于
     */
    public static boolean isGroup(String type, DbTypeGroup group) {
        try {
            DbType picked = DbType.valueOf(type.toUpperCase());
            return picked.getGroup() == group;
        } catch (Exception e) {
            return group == DbTypeGroup.OTHER;
        }
    }

    public static boolean isNumeric(String type) {
        return isGroup(type, DbTypeGroup.NUMERIC);
    }

    public static boolean isString(String type) {
        return isGroup(type, DbTypeGroup.STRING);
    }

    public static boolean isDate(String type) {
        return isGroup(type, DbTypeGroup.DATE);
    }
}
