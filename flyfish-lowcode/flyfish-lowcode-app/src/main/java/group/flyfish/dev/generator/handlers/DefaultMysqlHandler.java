package group.flyfish.dev.generator.handlers;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.common.exception.ServiceException;
import group.flyfish.dev.generator.bean.GeneratedTable;
import group.flyfish.dev.generator.properties.GeneratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 默认 mysql 代码生成处理器。
 *
 * @author wangyu
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultMysqlHandler implements GeneratorHandler {

    private static final String TEMPLATE_PREFIX = "generator/templates";

    private final GeneratorProperties properties;

    private final ViewContentFilter viewContentFilter = new ViewContentFilter();

    @Override
    public void generate() {
        if (CollectionUtils.isEmpty(properties.getTables())) {
            log.warn("未提供表元数据，跳过代码生成");
            return;
        }
        try {
            Configuration configuration = freemarker();
            GeneratedModelBuilder modelBuilder = new GeneratedModelBuilder(properties);
            for (DbTable table : filteredTables()) {
                renderTable(configuration, modelBuilder.build(table));
            }
        } catch (Exception e) {
            throw new ServiceException("生成代码时发生异常！" + e.getMessage(), e);
        }
    }

    private void renderTable(Configuration configuration, GeneratedTable table) throws Exception {
        Map<String, Object> context = context(table);
        viewContentFilter.filter(table, context);
        render(configuration, "entity.java.ftl", javaPath((String) context.get("entityPackage"), table.getEntityName() + ".java"), context);
        render(configuration, "mapper.java.ftl", javaPath((String) context.get("mapperPackage"), table.getMapperName() + ".java"), context);
        render(configuration, "service.java.ftl", javaPath((String) context.get("servicePackage"), table.getServiceName() + ".java"), context);
        render(configuration, "serviceImpl.java.ftl", javaPath((String) context.get("serviceImplPackage"), table.getServiceImplName() + ".java"), context);
        render(configuration, "controller.java.ftl", javaPath((String) context.get("controllerPackage"), table.getControllerName() + ".java"), context);
        render(configuration, "mapper.xml.ftl", packagePath((String) context.get("xmlPackage"), table.getMapperName() + ".xml"), context);
        render(configuration, "vue/index.vue.ftl", packagePath((String) context.get("frontPackage"), "index.vue"), context);
        render(configuration, "vue/form.js.ftl", packagePath((String) context.get("frontPackage"), "form.js"), context);
        render(configuration, "vue/detail.js.ftl", packagePath((String) context.get("frontPackage"), "detail.js"), context);
        render(configuration, "vue/api.js.ftl", packagePath((String) context.get("frontPackage"), "api.js"), context);
    }

    private Map<String, Object> context(GeneratedTable table) {
        GeneratorProperties.PackageProperties pkg = properties.getPackageConfig();
        Map<String, Object> packages = new HashMap<>();
        packages.put("Parent", pkg.getParent());
        packages.put("ModuleName", pkg.getModuleName());
        packages.put("Entity", fullPackage(pkg.getEntity()));
        packages.put("Service", fullPackage(pkg.getService()));
        packages.put("ServiceImpl", fullPackage(pkg.getServiceImpl()));
        packages.put("Mapper", fullPackage(pkg.getMapper()));
        packages.put("Xml", fullPackage(pkg.getXml()));
        packages.put("Controller", fullPackage(pkg.getController()));
        packages.put("Other", fullPackage(pkg.getOther()));

        String mapping = GeneratedModelBuilder.toHyphen(table.getEntityName());
        Map<String, Object> context = new HashMap<>();
        context.put("package", packages);
        context.put("entityPackage", packages.get("Entity"));
        context.put("servicePackage", packages.get("Service"));
        context.put("serviceImplPackage", packages.get("ServiceImpl"));
        context.put("mapperPackage", packages.get("Mapper"));
        context.put("xmlPackage", packages.get("Xml"));
        context.put("controllerPackage", packages.get("Controller"));
        context.put("frontPackage", packages.get("Other") + "." + mapping);
        context.put("table", table);
        context.put("entity", table.getEntityName());
        context.put("author", properties.getAuthor());
        context.put("date", LocalDate.now().format(DateTimeFormatter.ofPattern(properties.getCommentDate())));
        context.put("schemaName", "");
        context.put("swagger", properties.isEnableSwagger());
        context.put("kotlin", properties.isEnableKotlin());
        context.put("entityLombokModel", true);
        context.put("chainModel", true);
        context.put("entitySerialVersionUID", false);
        context.put("entityColumnConstant", true);
        context.put("activeRecord", false);
        context.put("idType", "ASSIGN_ID");
        context.put("restControllerStyle", true);
        context.put("controllerMappingHyphenStyle", true);
        context.put("controllerMappingHyphen", mapping);
        context.put("mapperAnnotation", true);
        context.put("superEntityClass", "Po");
        context.put("superMapperClass", "BaseMapper");
        context.put("superMapperClassPackage", "com.baomidou.mybatisplus.core.mapper.BaseMapper");
        context.put("superServiceClass", "IService");
        context.put("superServiceClassPackage", "com.baomidou.mybatisplus.extension.service.IService");
        context.put("superServiceImplClass", "ServiceImpl");
        context.put("superServiceImplClassPackage", "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl");
        context.put("enableCache", false);
        context.put("baseResultMap", true);
        context.put("baseColumnList", true);
        return context;
    }

    private List<DbTable> filteredTables() {
        return properties.getTables().stream()
                .filter(Objects::nonNull)
                .filter(table -> CollectionUtils.isEmpty(properties.getIncludes()) || properties.getIncludes().contains(table.getName()))
                .filter(table -> CollectionUtils.isEmpty(properties.getExcludes()) || !properties.getExcludes().contains(table.getName()))
                .filter(table -> StringUtils.isBlank(properties.getLikeTable()) || matchesLike(table.getName(), properties.getLikeTable()))
                .filter(table -> StringUtils.isBlank(properties.getNotLikeTable()) || !matchesLike(table.getName(), properties.getNotLikeTable()))
                .toList();
    }

    private boolean matchesLike(String tableName, String expression) {
        if (StringUtils.isBlank(expression)) {
            return false;
        }
        String pattern = StringUtils.substringBefore(expression, ",").trim()
                .replace("%", ".*")
                .replace("_", ".");
        return Pattern.compile(pattern).matcher(tableName).find();
    }

    private Configuration freemarker() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), TEMPLATE_PREFIX);
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return configuration;
    }

    private void render(Configuration configuration, String templateName, Path target, Map<String, Object> context)
            throws Exception {
        if (!properties.isFileOverride() && Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Template template = configuration.getTemplate(templateName);
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            template.process(context, writer);
        }
    }

    private Path javaPath(String packageName, String fileName) {
        return packagePath(packageName, fileName);
    }

    private Path packagePath(String packageName, String fileName) {
        return Path.of(StringUtils.defaultIfBlank(properties.getOutputDir(), "./generated"))
                .resolve(packageName.replace('.', '/'))
                .resolve(fileName);
    }

    private String fullPackage(String child) {
        GeneratorProperties.PackageProperties pkg = properties.getPackageConfig();
        return List.of(pkg.getParent(), pkg.getModuleName(), child).stream()
                .filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.joining("."));
    }
}
