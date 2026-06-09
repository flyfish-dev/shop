package group.flyfish.dev.ddl.parser;

import dev.flyfish.framework.beans.meta.parser.BeanPropertyAnnotations;
import dev.flyfish.framework.relational.mapping.Association;
import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.bean.DbRelation;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.bean.DbTable.DbColumn;
import group.flyfish.dev.ddl.mapping.DdlGenerator;
import group.flyfish.dev.enums.DbType;
import group.flyfish.dev.enums.DbTypeGroup;
import group.flyfish.dev.utils.type.TypeUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据库模型逆向建模工具
 *
 * @author wangyu
 */
@Slf4j
@RequiredArgsConstructor
public final class ModelParser {

    @Getter
    private final DbTable table = new DbTable();

    private final Object instance;

    private final Queue<Runnable> handlers = new LinkedList<>();

    private static final int DEFAULT_VARCHAR_LENGTH = 255;

    private static final int DEFAULT_ID_LENGTH = 36;

    private static final String FLYFISH_PROPERTY = "dev.flyfish.framework.annotations.Property";

    private static final String FLYFISH_ENTITY = "dev.flyfish.framework.annotations.Entity";

    private ModelParser(Class<?> modelClass) {
        this.instance = BeanUtils.instantiateClass(modelClass);
        parse(ResolvableType.forClass(modelClass));
    }

    /**
     * 解析入口，以class开始，后续都要保留泛型，防止擦除
     *
     * @param modelClass 模型
     * @return 结果
     */
    public static DbTable parse(Class<?> modelClass) {
        return new ModelParser(modelClass).getTable();
    }

    /**
     * 逆向解析class，完成元数据属性构建
     *
     * @param modelType 元模型类型
     */
    public void parse(ResolvableType modelType) {
        Class<?> modelClass = modelType.resolve();
        // 无类型，或者简单类型，直接返回
        if (null == modelClass || TypeUtils.isSimple(modelClass)) {
            return;
        }
        // 遍历声明的field，开始解析
        List<DbColumn> columns = Arrays.stream(modelClass.getDeclaredFields())
                // 获取字段类型
                .map(field -> {
                    ResolvableType type = ResolvableType.forField(field, modelType);
                    Class<?> fieldClass = type.resolve();
                    if (null == fieldClass) return null;
                    log.info("field {} {}", fieldClass.getCanonicalName(), field.getName());
                    // 得到当前属性转换后的column
                    return build(field, fieldClass);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        table.setColumns(columns);
        // 执行构建额外表
        while (!handlers.isEmpty()) {
            handlers.poll().run();
        }
        // 获取注解信息
        BeanPropertyAnnotations annotations = BeanPropertyAnnotations.from(modelClass);
        // 构建表基本信息
        annotations.as(Table.class)
                .map(annotation -> annotation.synthesize().name())
                .filter(StringUtils::isNotBlank)
                .then(table::setName)
                .empty(() -> table.setName(StringUtils.lowerCase(modelClass.getSimpleName())))
                .and()
                .as(FLYFISH_ENTITY)
                .map(property -> property.getString("title"))
                .then(table::setComment)
                .end();
        table.setIndexes(Collections.emptyList());
    }

    /**
     * 构建完整的字段
     *
     * @param field 字段信息
     * @return 结果
     */
    private DbColumn build(Field field, Class<?> resolvedType) {
        DbColumn column = new DbColumn();
        BeanPropertyAnnotations annotations = BeanPropertyAnnotations.from(field);
        // 如果是关联属性，跳过，如果是关系表，构建关系表
        boolean present = annotations.as(Association.class)
                .map(MergedAnnotation::synthesize)
                .filter(association -> StringUtils.isNotBlank(association.relationTable()))
                .then(association -> handlers.offer(() -> buildRelation(association)))
                .or()
                // 判断属性是否被排除，存在该注解则不向下执行
                .as(Transient.class)
                .end();
        // 如果调用链存在，则返回
        if (present) {
            return null;
        }
        // 设置字段和备注
        setName(column, field, annotations);
        // 设置备注
        setComment(column, field, annotations);
        // 设置正确的类型
        DbType type = getType(resolvedType, annotations);
        // 设置类型
        column.setType(type.name());
        // 设置长度
        setLength(column, type, annotations);
        // 设置额外属性
        setExtra(column, type, annotations);
        // 设置空状态
        column.setNullable(isNullable(annotations));
        // 设置默认值
        column.setDefaultValue(getDefaultValue(field));
        // 设置主键
        column.setPrimary(annotations.isPresent(Id.class));
        // 结果
        return column;
    }

    /**
     * 获取正确的名称
     *
     * @param field       字段信息
     * @param annotations 注解
     */
    private void setName(DbColumn column, Field field, BeanPropertyAnnotations annotations) {
        annotations
                .as(Column.class)
                .map(annotation -> annotation.synthesize().value())
                .filter(StringUtils::isNotBlank)
                .then(column::setName)
                .or()
                .as(FLYFISH_PROPERTY)
                .map(annotation -> annotation.getString("key"))
                .filter(StringUtils::isNotBlank)
                .then(column::setName)
                .or()
                .as(Property.class)
                .map(annotation -> annotation.synthesize().key())
                .filter(StringUtils::isNotBlank)
                .then(column::setName)
                .empty(() -> column.setName(field.getName()))
                .end();
    }

    /**
     * 设置正确的备注
     *
     * @param column      列
     * @param field       字段
     * @param annotations 注解集合
     */
    private void setComment(DbColumn column, Field field, BeanPropertyAnnotations annotations) {
        annotations
                .as(FLYFISH_PROPERTY)
                .map(annotation -> annotation.getString("title"))
                .filter(StringUtils::isNotBlank)
                .then(column::setComment)
                .or()
                .as(Property.class)
                .map(annotation -> annotation.synthesize().title())
                .filter(StringUtils::isNotBlank)
                .then(column::setComment)
                .empty(() -> column.setComment(field.getName()))
                .end();
    }

    /**
     * 获取长度
     * 可从 @Length
     *
     * @param annotations 注解集合
     */
    private void setLength(DbColumn column, DbType type, BeanPropertyAnnotations annotations) {
        if (type.getGroup() != DbTypeGroup.STRING) return;
        // 根据注解直接设置
        annotations.as(Id.class)
                .exists(() -> column.setLength(DEFAULT_ID_LENGTH))
                .or()
                .as(Size.class)
                .map(annotation -> annotation.synthesize().max())
                .then(column::setLength)
                .empty(() -> column.setLength(DEFAULT_VARCHAR_LENGTH))
                .end();

    }

    /**
     * 设置额外属性
     *
     * @param column      列
     * @param type        类型
     * @param annotations 注解
     */
    private void setExtra(DbColumn column, DbType type, BeanPropertyAnnotations annotations) {
        switch (type) {
            case VARCHAR:
                column.setCharacterSet("utf8mb4");
                column.setCollation("utf8mb4_bin");
                break;
        }
    }

    /**
     * 获取默认值
     *
     * @param field 字段
     * @return 结果
     */
    private String getDefaultValue(Field field) {
        ReflectionUtils.makeAccessible(field);
        Object value = ReflectionUtils.getField(field, instance);
        if (null != value) return String.valueOf(value);
        return null;
    }

    /**
     * 获取空值条件
     * 当遇到 @NotNull @NotEmpty @NotBlank等，设置非空
     *
     * @param annotations 注解集合
     * @return 结果
     */
    private boolean isNullable(BeanPropertyAnnotations annotations) {
        boolean notNull = Stream.of(NotNull.class, NonNull.class, NotBlank.class, NotEmpty.class, Id.class)
                .anyMatch(annotations::isPresent);
        return !notNull;
    }

    /**
     * 严格分析对象字段类型
     *
     * @param type 类型
     * @return 结果
     */
    private DbType getType(Class<?> type, BeanPropertyAnnotations annotations) {
        // 处理枚举的情况
        if (type.isEnum()) {
            // 基本枚举
            return DbType.TINYINT;
        }
        // 处理简单类型，无法匹配默认字符串
        if (TypeUtils.isSimple(type)) {
            return Arrays.stream(DbType.values()).filter(value -> value.supports(type)).findFirst()
                    .orElse(DbType.VARCHAR);
        }
        // 其余情况一律对象，保持空解析（占位）
        return DbType.JSON;
    }

    /**
     * 构建关系表
     */
    private void buildRelation(Association association) {
        // 首先确认当前表有主键
        Optional<DbColumn> primary = table.firstPrimaryColumn();
        if (primary.isPresent()) {
            DbTable relation = new DbTable();
            relation.setName(association.relationTable());
            relation.setComment("关系表");
            relation.setColumns(Stream.of(association.field(), association.foreignField()).map(field -> {
                DbColumn column = new DbColumn();
                column.setName(field);
                column.setPrimary(true);
                column.setType(primary.get().getType());
                column.setLength(primary.get().getLength());
                column.setNullable(false);
                return column;
            }).collect(Collectors.toList()));
            table.addRelation(new DbRelation(relation, DbRelation.Type.MANY_TO_MANY));
        }
    }

    @Table("test_table")
    private static class Test {

        @Id
        @Property("主键")
        private String name;

        @NotNull
        @Property("座位")
        private Long sit;

        @Column("tag_list")
        @Property("标签列表")
        private List<String> tags;

        @Size(max = 1000)
        @Property("描述")
        private String description = "暂无";
    }

    public static void main(String[] args) {
        DbTable table = ModelParser.parse(Test.class);
        table.setLogical(true);
        System.out.println(DdlGenerator.getCreateSql(table));
    }
}
