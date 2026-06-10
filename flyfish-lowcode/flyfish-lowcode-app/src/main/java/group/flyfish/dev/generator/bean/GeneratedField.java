package group.flyfish.dev.generator.bean;

import lombok.Data;

@Data
public class GeneratedField {

    private String name;

    private String columnName;

    private String annotationColumnName;

    private String propertyName;

    private String capitalName;

    private String propertyType;

    private String type;

    private String comment;

    private boolean keyFlag;

    private boolean keyIdentityFlag;

    private boolean convert;

    private String fill;

    private boolean versionField;

    private boolean logicDeleteField;

    private FieldMetaInfo metaInfo = new FieldMetaInfo();

    @Data
    public static class FieldMetaInfo {

        private boolean nullable = true;
    }
}
