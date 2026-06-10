package group.flyfish.dev.generator.management.manager;

import group.flyfish.dev.bean.DbSource;

/**
 * 主键生成器
 *
 * @author wangyu
 */
public interface DbSourceKeyGenerator {

    /**
     * 生成数据源key
     *
     * @param source 数据源信息
     * @return 结果
     */
    String generate(DbSource source);
}
