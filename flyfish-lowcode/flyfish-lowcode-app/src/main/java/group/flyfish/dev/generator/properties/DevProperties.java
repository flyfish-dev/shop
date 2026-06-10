package group.flyfish.dev.generator.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 开发配置信息
 *
 * @author wangyu
 */
@ConfigurationProperties(prefix = "dev")
@Data
public class DevProperties {

    // 缓存地址
    private String cachePath;
}
