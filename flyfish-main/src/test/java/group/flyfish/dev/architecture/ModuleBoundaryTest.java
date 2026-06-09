package group.flyfish.dev.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryTest {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([^;]+);");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+([^;]+);");
    private static final Pattern CONFIG_IMPORT_PATTERN = Pattern.compile("-\\s*classpath:/config/([^\\s]+)");
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z_][a-zA-Z0-9_]*)`?");
    private static final Pattern ALTER_TABLE_PATTERN = Pattern.compile("(?i)ALTER\\s+TABLE\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");
    private static final Pattern COMMENT_TABLE_PATTERN = Pattern.compile("(?i)COMMENT\\s+ON\\s+TABLE\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");
    private static final Pattern CREATE_INDEX_PATTERN = Pattern.compile("(?i)CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?[a-zA-Z_][a-zA-Z0-9_]*`?\\s+ON\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");
    private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("(?im)^\\s*UPDATE\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");

    private final Path root = moduleRoot();

    @Test
    void internalMavenDependenciesFollowModuleBoundaries() throws Exception {
        Map<String, Set<String>> expected = Map.of(
                "flyfish-portal-api", Set.of(),
                "flyfish-common", Set.of("flyfish-ddl"),
                "flyfish-auth", Set.of("flyfish-common"),
                "flyfish-platform", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-auth"),
                "flyfish-git", Set.of("flyfish-common", "flyfish-auth"),
                "flyfish-lowcode", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-auth"),
                "flyfish-shop", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-auth", "flyfish-git"),
                "flyfish-lowcode-app", Set.of("flyfish-common", "flyfish-platform", "flyfish-lowcode"),
                "flyfish-shop-app", Set.of("flyfish-common", "flyfish-platform", "flyfish-shop"),
                "flyfish-main", Set.of("flyfish-common", "flyfish-platform", "flyfish-lowcode", "flyfish-shop")
        );

        Map<String, Set<String>> actual = new HashMap<>();
        for (String module : expected.keySet()) {
            actual.put(module, internalDependencies(root.resolve(module).resolve("pom.xml")));
        }

        assertEquals(expected, actual);
    }

    @Test
    void sourceImportsDoNotCrossBusinessModuleBoundaries() throws Exception {
        Map<String, List<String>> forbiddenImports = Map.of(
                "flyfish-common", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.",
                        "group.flyfish.dev.portal.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user."
                ),
                "flyfish-portal-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user."
                ),
                "flyfish-auth", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.portal.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support."
                ),
                "flyfish-platform", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support."
                ),
                "flyfish-git", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.portal.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support."
                ),
                "flyfish-lowcode", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support."
                ),
                "flyfish-shop", List.of(
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.lowcode.",
                        "group.flyfish.dev.portal.controller.",
                        "group.flyfish.dev.portal.domain.",
                        "group.flyfish.dev.portal.service.",
                        "group.flyfish.dev.portal.support."
                )
        );

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : forbiddenImports.entrySet()) {
            Path sourceRoot = root.resolve(entry.getKey()).resolve("src/main/java");
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (var stream = Files.walk(sourceRoot)) {
                for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int index = 0; index < lines.size(); index++) {
                        Matcher matcher = IMPORT_PATTERN.matcher(lines.get(index).trim());
                        if (!matcher.matches()) {
                            continue;
                        }
                        String imported = matcher.group(1);
                        for (String forbidden : entry.getValue()) {
                            if (imported.startsWith(forbidden)) {
                                violations.add(root.relativize(file) + ":" + (index + 1) + " imports " + imported);
                            }
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void mainSourcePackagesAreOwnedBySingleModulesExceptApplicationBootstraps() throws Exception {
        Map<String, Set<String>> packageOwners = new HashMap<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .toList()) {
                String owner = moduleName(file);
                String packageName = packageName(file);
                if (!owner.isBlank() && !packageName.isBlank()) {
                    packageOwners.computeIfAbsent(packageName, ignored -> new HashSet<>()).add(owner);
                }
            }
        }

        Set<String> applicationModules = Set.of("flyfish-lowcode-app", "flyfish-shop-app", "flyfish-main");
        List<String> violations = packageOwners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> !("group.flyfish.dev".equals(entry.getKey())
                        && entry.getValue().equals(applicationModules)))
                .map(entry -> entry.getKey() + " owned by " + entry.getValue())
                .sorted()
                .toList();

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void sharedModulesDoNotReferenceBusinessImplementationsByName() throws Exception {
        Map<String, List<String>> forbiddenTokens = Map.of(
                "flyfish-portal-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user.",
                        "/shops/",
                        "飞鱼小铺"
                ),
                "flyfish-common", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user.",
                        "/shops/",
                        "飞鱼小铺"
                ),
                "flyfish-auth", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "Shop",
                        "shop",
                        "/shop",
                        "飞鱼小铺"
                ),
                "flyfish-git", List.of(
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "Shop",
                        "shop",
                        "/shops/",
                        "飞鱼小铺"
                )
        );

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : forbiddenTokens.entrySet()) {
            Path moduleRoot = root.resolve(entry.getKey()).resolve("src/main");
            if (!Files.exists(moduleRoot)) {
                continue;
            }
            try (var stream = Files.walk(moduleRoot)) {
                for (Path file : stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.toString().endsWith(".class"))
                        .toList()) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    for (String forbidden : entry.getValue()) {
                        if (content.contains(forbidden)) {
                            violations.add(root.relativize(file) + " contains " + forbidden);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void productModulesDoNotExposeOtherProductLabelsOrRoutes() throws Exception {
        Map<String, List<String>> forbiddenTokens = Map.of(
                "flyfish-lowcode", List.of(
                        "飞鱼小铺",
                        "Shop",
                        "shop",
                        "/shop",
                        "/shops"
                ),
                "flyfish-shop", List.of(
                        "飞鱼低代码平台",
                        "Lowcode",
                        "lowcode",
                        "/model-design",
                        "/code-generate",
                        "/online-launch",
                        "/integrate-test",
                        "/portal/workbench",
                        "/integrity/"
                )
        );

        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : forbiddenTokens.entrySet()) {
            Path moduleRoot = root.resolve(entry.getKey()).resolve("src/main");
            if (!Files.exists(moduleRoot)) {
                continue;
            }
            try (var stream = Files.walk(moduleRoot)) {
                for (Path file : stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.toString().endsWith(".class"))
                        .toList()) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    for (String forbidden : entry.getValue()) {
                        if (content.contains(forbidden)) {
                            violations.add(root.relativize(file) + " contains " + forbidden);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void appConfigImportsMatchComposedModules() throws Exception {
        Map<String, List<String>> expected = Map.of(
                "flyfish-lowcode-app", List.of(
                        "flyfish-common.yml",
                        "flyfish-platform.yml",
                        "flyfish-auth.yml",
                        "flyfish-lowcode.yml"
                ),
                "flyfish-shop-app", List.of(
                        "flyfish-common.yml",
                        "flyfish-platform.yml",
                        "flyfish-auth.yml",
                        "flyfish-git.yml",
                        "flyfish-shop.yml"
                ),
                "flyfish-main", List.of(
                        "flyfish-common.yml",
                        "flyfish-platform.yml",
                        "flyfish-auth.yml",
                        "flyfish-git.yml",
                        "flyfish-lowcode.yml",
                        "flyfish-shop.yml"
                )
        );

        Map<String, List<String>> actual = new HashMap<>();
        for (String module : expected.keySet()) {
            Path application = root.resolve(module).resolve("src/main/resources/application.yml");
            actual.put(module, configImports(application));
        }

        assertEquals(expected, actual);
    }

    @Test
    void schemaTablesAreDeclaredByOneModuleOnly() throws Exception {
        Map<String, List<String>> declarations = new HashMap<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".sql"))
                    .filter(path -> path.toString().contains("/src/main/resources/schema/"))
                    .toList()) {
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
                while (matcher.find()) {
                    declarations.computeIfAbsent(matcher.group(1), table -> new ArrayList<>())
                            .add(root.relativize(file).toString());
                }
            }
        }

        List<String> duplicates = declarations.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " declared in " + entry.getValue())
                .sorted()
                .toList();

        assertTrue(duplicates.isEmpty(), () -> String.join(System.lineSeparator(), duplicates));
    }

    @Test
    void databaseScriptsOnlyReferenceTablesOwnedBySameModule() throws Exception {
        Map<String, String> tableOwners = schemaTableOwners();
        List<String> violations = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".sql"))
                    .filter(path -> path.toString().contains("/src/main/resources/schema/")
                            || path.toString().contains("/src/main/resources/dialect/"))
                    .toList()) {
                String owner = moduleName(file);
                if (owner.isBlank()) {
                    continue;
                }
                for (String table : referencedTables(file)) {
                    String tableOwner = tableOwners.get(table);
                    if (tableOwner == null) {
                        violations.add(root.relativize(file) + " references unknown table " + table);
                        continue;
                    }
                    if (!owner.equals(tableOwner)) {
                        violations.add(root.relativize(file) + " references " + table
                                + " owned by " + tableOwner);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void appModulesDoNotCarryBusinessSchemaScripts() throws Exception {
        List<String> apps = List.of("flyfish-lowcode-app", "flyfish-shop-app", "flyfish-main");
        List<String> schemaScripts = new ArrayList<>();
        for (String app : apps) {
            Path resources = root.resolve(app).resolve("src/main/resources");
            if (!Files.exists(resources)) {
                continue;
            }
            try (var stream = Files.walk(resources)) {
                stream
                        .filter(path -> path.toString().endsWith(".sql"))
                        .filter(path -> path.toString().contains("/schema/")
                                || path.toString().contains("/dialect/"))
                        .map(root::relativize)
                        .map(Path::toString)
                        .forEach(schemaScripts::add);
            }
        }

        assertTrue(schemaScripts.isEmpty(), () -> String.join(System.lineSeparator(), schemaScripts));
    }

    @Test
    void appModulesDoNotCarrySharedStaticOrTemplateResources() throws Exception {
        List<String> apps = List.of("flyfish-lowcode-app", "flyfish-shop-app", "flyfish-main");
        List<String> duplicatedResources = new ArrayList<>();
        for (String app : apps) {
            Path resources = root.resolve(app).resolve("src/main/resources");
            if (!Files.exists(resources)) {
                continue;
            }
            try (var stream = Files.walk(resources)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().contains("/static/")
                                || path.toString().contains("/templates/"))
                        .map(root::relativize)
                        .map(Path::toString)
                        .forEach(duplicatedResources::add);
            }
        }

        assertTrue(duplicatedResources.isEmpty(), () -> String.join(System.lineSeparator(), duplicatedResources));
    }

    @Test
    void appModulesOnlyCarryBootstrapSources() throws Exception {
        Map<String, Set<String>> expected = Map.of(
                "flyfish-lowcode-app", Set.of("src/main/java/group/flyfish/dev/FlyfishLowcodeApplication.java"),
                "flyfish-shop-app", Set.of("src/main/java/group/flyfish/dev/FlyfishShopApplication.java"),
                "flyfish-main", Set.of("src/main/java/group/flyfish/dev/FlyfishDevApplication.java")
        );

        Map<String, Set<String>> actual = new HashMap<>();
        for (String app : expected.keySet()) {
            Path appRoot = root.resolve(app);
            Path sourceRoot = appRoot.resolve("src/main/java");
            Set<String> sources = new HashSet<>();
            if (Files.exists(sourceRoot)) {
                try (var stream = Files.walk(sourceRoot)) {
                    stream
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(appRoot::relativize)
                            .map(Path::toString)
                            .forEach(sources::add);
                }
            }
            actual.put(app, sources);
        }

        assertEquals(expected, actual);
    }

    @Test
    void mainAppOnlyCarriesCompositionTests() throws Exception {
        Set<String> expected = Set.of(
                "src/test/java/group/flyfish/dev/FlyfishDevApplicationTests.java",
                "src/test/java/group/flyfish/dev/FlyfishRepoTest.java",
                "src/test/java/group/flyfish/dev/architecture/ModuleBoundaryTest.java"
        );

        Path appRoot = root.resolve("flyfish-main");
        Path testRoot = appRoot.resolve("src/test/java");
        Set<String> tests = new HashSet<>();
        if (Files.exists(testRoot)) {
            try (var stream = Files.walk(testRoot)) {
                stream
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(appRoot::relativize)
                        .map(Path::toString)
                        .forEach(tests::add);
            }
        }

        assertEquals(expected, tests);
    }

    private Set<String> internalDependencies(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        NodeList dependencies = document.getElementsByTagName("dependency");
        Set<String> result = new HashSet<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if ("group.flyfish".equals(text(dependency, "groupId"))) {
                result.add(text(dependency, "artifactId"));
            }
        }
        return result;
    }

    private List<String> configImports(Path application) throws Exception {
        List<String> result = new ArrayList<>();
        String source = Files.readString(application, StandardCharsets.UTF_8);
        Matcher matcher = CONFIG_IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private Map<String, String> schemaTableOwners() throws Exception {
        Map<String, String> result = new HashMap<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(path -> path.toString().endsWith(".sql"))
                    .filter(path -> path.toString().contains("/src/main/resources/schema/"))
                    .toList()) {
                String owner = moduleName(file);
                String sql = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
                while (matcher.find()) {
                    result.put(matcher.group(1), owner);
                }
            }
        }
        return result;
    }

    private Set<String> referencedTables(Path file) throws Exception {
        String sql = Files.readString(file, StandardCharsets.UTF_8);
        Set<String> result = new HashSet<>();
        collectMatches(result, CREATE_TABLE_PATTERN, sql);
        collectMatches(result, ALTER_TABLE_PATTERN, sql);
        collectMatches(result, COMMENT_TABLE_PATTERN, sql);
        collectMatches(result, CREATE_INDEX_PATTERN, sql);
        collectMatches(result, UPDATE_TABLE_PATTERN, sql);
        return result;
    }

    private void collectMatches(Set<String> result, Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
    }

    private String moduleName(Path file) {
        Path relative = root.relativize(file);
        return relative.getNameCount() == 0 ? "" : relative.getName(0).toString();
    }

    private String packageName(Path file) throws Exception {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            Matcher matcher = PACKAGE_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private Path moduleRoot() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(userDir.resolve("pom.xml")) && userDir.getFileName().toString().equals("flyfish-main")) {
            return userDir.getParent();
        }
        return userDir;
    }
}
