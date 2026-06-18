package group.flyfish.dev.bean;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 数据连接源
 *
 * @author wangyu
 */
@Data
@Accessors(chain = true)
@Table("db_source")
public class DbSource {

    @Id
    private Long id;

    @Column("source_key")
    private String key;

    @Transient
    private transient String error;

    private String name;

    /**
     * 兼容旧数据和底层连接使用的派生连接串，前端不再直接维护。
     */
    private String url;

    private String type = "mysql";

    private String host;

    private Integer port;

    private String databaseName;

    private String params;

    private String username;

    private String password;

    private String owner = "public";

    public DbSource() {

    }

    public DbSource(String key) {
        this.key = key;
    }
}
