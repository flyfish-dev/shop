package group.flyfish.dev.generator.bean;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GeneratedTable {

    private String name;

    private String comment;

    private String entityName;

    private String entityPath;

    private String controllerName;

    private String serviceName;

    private String serviceImplName;

    private String mapperName;

    private boolean convert = true;

    private List<GeneratedField> fields = new ArrayList<>();

    private List<GeneratedField> commonFields = new ArrayList<>();

    private List<String> importPackages = new ArrayList<>();

    private String fieldNames;
}
