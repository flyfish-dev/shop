package group.flyfish.dev.generator.properties;

import group.flyfish.dev.bean.DbTable;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成器配置项
 */
@ConfigurationProperties(prefix = "generator")
@Data
public class GeneratorProperties {

    /**
     * 是否启动时生成
     */
    private Boolean auto;

    /**
     * r2dbc配置，主要用于启动时自动生成。
     */
    private R2dbcProperties r2dbc;

    /**
     * 包配置
     */
    private PackageProperties packageConfig = new PackageProperties();

    /**
     * 表名like匹配
     */
    private String likeTable;

    /**
     * 表名not like 过滤
     */
    private String notLikeTable;

    /**
     * 表匹配（include 与 exclude 只能配置一项）
     */
    private List<String> includes = new ArrayList<>();

    /**
     * 排除匹配(内存过滤)（include 与 exclude 只能配置一项）
     */
    private List<String> excludes = new ArrayList<>();

    /**
     * 过滤表前缀
     */
    private List<String> tablePrefix = new ArrayList<>();

    /**
     * 过滤表后缀
     */
    private List<String> tableSuffix = new ArrayList<>();

    /**
     * 覆盖已生成文件
     */
    private boolean fileOverride = false;

    /**
     * 禁止打开输出目录
     */
    private boolean disableOpenDir = true;

    /**
     * 指定输出目录
     */
    private String outputDir;

    /**
     * 作者名
     */
    private String author = "wangyu";

    /**
     * 开启 kotlin 模式
     */
    private boolean enableKotlin = false;

    /**
     * 开启 swagger 模式
     */
    private boolean enableSwagger = false;

    /**
     * 时间策略：ONLY_DATE / SQL_PACK / TIME_PACK。
     */
    private String dateType = "ONLY_DATE";

    /**
     * 注释日期
     */
    private String commentDate = "yyyy-MM-dd";

    /**
     * 已解析的数据表。在线生成时由控制器基于 R2DBC 元数据填充。
     */
    private List<DbTable> tables = new ArrayList<>();

    @Data
    public static class PackageProperties {

        /**
         * 父包名
         */
        private String parent = "com.chinaunicom.system";
        /**
         * 父包模块名
         */
        private String moduleName = "";
        /**
         * Entity 包名
         */
        private String entity = "domain.po";
        /**
         * Service 包名
         */
        private String service = "service";
        /**
         * Service Impl 包名
         */
        private String serviceImpl = "service.impl";
        /**
         * Mapper 包名
         */
        private String mapper = "mapper";
        /**
         * Mapper XML 包名
         */
        private String xml = "mapper.mapping";
        /**
         * Controller 包名
         */
        private String controller = "controller";
        /**
         * 自定义文件包名，输出自定义文件时所用到的包名
         */
        private String other = "other";

        /**
         * 路径配置信息
         */
        private Map<String, String> pathInfo = new HashMap<>();
    }

    @Data
    public static class R2dbcProperties {

        private String url;
        private String username;
        private String password;
        private String schema;
    }
}
