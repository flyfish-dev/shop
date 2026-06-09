package group.flyfish.dev.bean;

import lombok.Data;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 数据库连接字符串
 *
 * @author wangyu
 */
@Data
public class DbURL {

    private String protocol;

    private UriComponentsBuilder builder;

    public static DbURL create(String url) {
        int prefix = url.indexOf(":");
        DbURL dbURL = new DbURL();
        dbURL.setProtocol(url.substring(0, prefix));
        dbURL.setBuilder(UriComponentsBuilder.fromUriString(url.substring(prefix + 1)));
        return dbURL;
    }

    public DbURL query(String key, String value) {
        builder.replaceQueryParam(key, value);
        return this;
    }

    @Override
    public String toString() {
        return protocol + ":" + builder.build();
    }
}
