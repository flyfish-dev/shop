package group.flyfish.dev.bean;

import lombok.Data;

/**
 * 数据库关联关系
 *
 * @author wangyu
 */
@Data
public class DbRelation {

    // 目的表
    private DbTable target;

    // 关联关系
    private Type type;

    // 内部字段
    private String field;

    // 外部字段
    private String foreignField;

    public DbRelation(DbTable target, Type type) {
        this.target = target;
        this.type = type;
    }

    /**
     * 关联类型
     */
    public enum Type {

        ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
    }
}
