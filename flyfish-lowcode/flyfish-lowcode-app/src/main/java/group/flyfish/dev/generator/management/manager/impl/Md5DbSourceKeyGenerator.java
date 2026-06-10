package group.flyfish.dev.generator.management.manager.impl;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.manager.DbSourceKeyGenerator;
import group.flyfish.dev.generator.management.utils.R2dbcUrlUtils;
import group.flyfish.dev.utils.encrypt.MD5Util;
import org.springframework.stereotype.Component;

/**
 * 基于md5的简单key生成
 *
 * @author wangyu
 */
@Component
public class Md5DbSourceKeyGenerator implements DbSourceKeyGenerator {

    /**
     * 生成数据源key
     *
     * @param source 数据源信息
     * @return 结果
     */
    @Override
    public String generate(DbSource source) {
        return MD5Util.MD5Encode(source.getUsername() + ":" + R2dbcUrlUtils.connectionUrl(source), "UTF-8");
    }
}
