package group.flyfish.dev.generator.handlers;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.generator.bean.GeneratedField;
import group.flyfish.dev.generator.bean.GeneratedTable;
import group.flyfish.dev.generator.properties.GeneratorProperties;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

class GeneratedModelBuilder {

    private static final Set<String> SUPER_COLUMNS = Set.of("id", "create_by", "create_time", "update_by", "update_time", "is_delete");

    private final GeneratorProperties properties;

    GeneratedModelBuilder(GeneratorProperties properties) {
        this.properties = properties;
    }

    GeneratedTable build(DbTable source) {
        String entity = toPascal(stripAffixes(source.getName()));
        GeneratedTable table = new GeneratedTable();
        table.setName(source.getName());
        table.setComment(source.getComment());
        table.setEntityName(entity);
        table.setEntityPath(toCamel(entity));
        table.setControllerName(entity + "Controller");
        table.setServiceName(entity + "Service");
        table.setServiceImplName(entity + "ServiceImpl");
        table.setMapperName(entity + "Mapper");

        List<GeneratedField> fields = new ArrayList<>();
        List<GeneratedField> commonFields = new ArrayList<>();
        for (DbTable.DbColumn column : source.columns().toList()) {
            GeneratedField field = buildField(column);
            if (SUPER_COLUMNS.contains(column.getName())) {
                commonFields.add(field);
            } else {
                fields.add(field);
            }
        }
        table.setFields(fields);
        table.setCommonFields(commonFields);
        table.setFieldNames(fields.stream().map(GeneratedField::getColumnName).collect(Collectors.joining(", ")));
        table.setImportPackages(imports(fields));
        return table;
    }

    private GeneratedField buildField(DbTable.DbColumn column) {
        String property = toCamel(column.getName());
        GeneratedField field = new GeneratedField();
        field.setName(column.getName());
        field.setColumnName(column.getName());
        field.setAnnotationColumnName(column.getName());
        field.setPropertyName(property);
        field.setCapitalName(StringUtils.capitalize(property));
        field.setPropertyType(javaType(column));
        field.setType(column.getType());
        field.setComment(StringUtils.defaultString(column.getComment()));
        field.setKeyFlag(BooleanUtils.isTrue(column.getPrimary()));
        field.setKeyIdentityFlag(BooleanUtils.isTrue(column.getAutoIncrement()));
        field.setConvert(!column.getName().equals(property));
        field.getMetaInfo().setNullable(BooleanUtils.isNotFalse(column.getNullable()));
        return field;
    }

    private List<String> imports(List<GeneratedField> fields) {
        Set<String> imports = new HashSet<>();
        imports.add("com.baomidou.mybatisplus.annotation.*");
        imports.add("group.flyfish.dev.common.base.domain.Po");
        for (GeneratedField field : fields) {
            switch (field.getPropertyType()) {
                case "BigDecimal" -> imports.add(BigDecimal.class.getName());
                case "LocalDate" -> imports.add(LocalDate.class.getName());
                case "LocalDateTime" -> imports.add(LocalDateTime.class.getName());
                case "LocalTime" -> imports.add(LocalTime.class.getName());
                case "Date" -> imports.add(Date.class.getName());
                default -> {
                }
            }
        }
        return imports.stream().sorted().toList();
    }

    private String javaType(DbTable.DbColumn column) {
        String type = StringUtils.lowerCase(column.getType(), Locale.ROOT);
        if (type == null) {
            return "String";
        }
        if (type.contains("bigint")) {
            return "Long";
        }
        if (type.contains("int")) {
            return "Integer";
        }
        if (type.contains("decimal") || type.contains("numeric")) {
            return "BigDecimal";
        }
        if (type.contains("double")) {
            return "Double";
        }
        if (type.contains("float")) {
            return "Float";
        }
        if (type.contains("bit") || ("tinyint".equals(type) && Integer.valueOf(1).equals(column.getLength()))) {
            return "Boolean";
        }
        if (type.contains("date") || type.contains("time")) {
            return switch (StringUtils.defaultString(properties.getDateType(), "ONLY_DATE")) {
                case "TIME_PACK" -> {
                    if ("date".equals(type)) {
                        yield "LocalDate";
                    }
                    if ("time".equals(type)) {
                        yield "LocalTime";
                    }
                    yield "LocalDateTime";
                }
                default -> "Date";
            };
        }
        if (type.contains("binary") || type.contains("blob")) {
            return "byte[]";
        }
        return "String";
    }

    private String stripAffixes(String tableName) {
        String value = tableName;
        for (String prefix : properties.getTablePrefix()) {
            if (StringUtils.startsWith(value, prefix)) {
                value = StringUtils.removeStart(value, prefix);
                break;
            }
        }
        for (String suffix : properties.getTableSuffix()) {
            if (StringUtils.endsWith(value, suffix)) {
                value = StringUtils.removeEnd(value, suffix);
                break;
            }
        }
        return value;
    }

    static String toPascal(String value) {
        return java.util.Arrays.stream(StringUtils.split(StringUtils.defaultString(value), "_- "))
                .filter(StringUtils::isNotBlank)
                .map(part -> StringUtils.capitalize(part.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining());
    }

    static String toCamel(String value) {
        String pascal = value.contains("_") || value.contains("-") ? toPascal(value) : value;
        return StringUtils.uncapitalize(pascal);
    }

    static String toHyphen(String value) {
        String snake = value.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replace('_', '-');
        return snake.toLowerCase(Locale.ROOT);
    }
}
