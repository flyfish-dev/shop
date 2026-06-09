package group.flyfish.dev.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据库表
 *
 * @author wangyu
 */
@Data
@ApiModel("数据库表实体")
public class DbTable extends Changeable<DbTable> {

    private static final DbColumn LOGICAL_FIELD = logicalField();

    @ApiModelProperty("表名")
    private String name;

    @ApiModelProperty("表备注")
    private String comment;

    @ApiModelProperty("表的列")
    private List<DbColumn> columns;

    @ApiModelProperty("表索引")
    private List<DbIndex> indexes;

    @ApiModelProperty("表关联关系")
    private List<DbRelation> relations;

    @ApiModelProperty("启用逻辑删除")
    private boolean logical = false;

    private transient String dataSource;

    private transient String error;

    public Optional<DbColumn> firstPrimaryColumn() {
        if (CollectionUtils.isNotEmpty(columns)) {
            return columns.stream().filter(column -> BooleanUtils.isTrue(column.getPrimary())).findFirst();
        }
        return Optional.empty();
    }

    /**
     * 从表中获取主键标识
     * 该方法仅支持单主键
     *
     * @return 结果
     */
    public DbIndex primaryKey() {
        if (CollectionUtils.isNotEmpty(columns)) {
            List<String> fields = columns.stream().filter(column -> BooleanUtils.isTrue(column.getPrimary()))
                    .map(column -> column.name)
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(fields)) {
                DbIndex index = new DbIndex();
                index.setType(DbIndexType.PRIMARY);
                index.setFields(fields);
                index.setComment("主键");
                return index;
            }
        }
        return null;
    }

    public void setPrimaryKeyColumn(String column) {
        if (null != columns) {
            for (DbColumn col : columns) {
                if (col.getName().equalsIgnoreCase(column)) {
                    col.setPrimary(true);
                }
            }
        }
    }

    public void addRelation(DbRelation relation) {
        if (null == relations) {
            relations = new ArrayList<>();
        }
        relations.add(relation);
    }

    public void addIndex(DbIndex index) {
        if (null == indexes) {
            indexes = new ArrayList<>();
        }
        indexes.add(index);
    }

    public Stream<DbColumn> columns() {
        if (this.logical) {
            return Stream.concat(columns.stream(), Stream.of(LOGICAL_FIELD));
        }
        return columns.stream();
    }

    private static DbColumn logicalField() {
        DbColumn column = new DbColumn();
        column.setName("delete");
        column.setType("bit");
        column.setLength(1);
        column.setComment("逻辑删除标记");
        column.setNullable(false);
        column.setDefaultValue("b'0'");
        return column;
    }

    /**
     * 要实现比较，必须实现的方法
     *
     * @return 可比较的方法引用
     */
    @Override
    public List<CompareField<DbTable, Object>> comparable() {
        return Arrays.asList(DbTable::getName, DbTable::getComment);
    }


    /**
     * 索引类型
     * mysql仅memory表支持hash索引，不考虑
     */
    @Getter
    @AllArgsConstructor
    public enum DbIndexType {

        UNIQUE("UNIQUE"), BTREE(null), FULLTEXT("FULLTEXT"), PRIMARY(null);

        private final String code;
    }

    /**
     * 数据库列
     */
    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class DbColumn extends Changeable<DbColumn> {

        private String name;

        private String type;

        private Integer length;

        private Integer precision;

        private Boolean primary = false;

        private Boolean nullable;

        private String defaultValue;

        private String characterSet;

        private String collation;

        private String comment;

        private Boolean unsigned;

        private Boolean autoIncrement;

        // 所在位置，为具体字段
        @JsonIgnore
        private transient String after;

        /**
         * 要实现比较，必须实现的方法
         *
         * @return 可比较的方法引用
         */
        @Override
        public List<CompareField<DbColumn, Object>> comparable() {
            return Arrays.asList(DbColumn::getType, DbColumn::getLength,
                    DbColumn::getPrecision, DbColumn::getNullable, DbColumn::getDefaultValue,
                    DbColumn::getCharacterSet, DbColumn::getCollation, DbColumn::getComment, DbColumn::getUnsigned,
                    DbColumn::getAutoIncrement, DbColumn::getAfter);
        }
    }

    /**
     * 数据库索引
     */
    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class DbIndex extends Changeable<DbIndex> {

        private String name;

        private List<String> fields;

        private DbIndexType type;

        private String comment;

        /**
         * 要实现比较，必须实现的方法
         *
         * @return 可比较的方法引用
         */
        @Override
        public List<CompareField<DbIndex, Object>> comparable() {
            return Arrays.asList(DbIndex::getFields, DbIndex::getType, DbIndex::getComment);
        }

        /**
         * 获取目标类型
         *
         * @return 结果
         */
        public String target() {
            if (type == DbIndexType.PRIMARY) {
                return "PRIMARY KEY";
            }
            return isCreating() ? "KEY" : "INDEX";
        }
    }
}
