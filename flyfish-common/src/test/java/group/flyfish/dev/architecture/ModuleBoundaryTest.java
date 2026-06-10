package group.flyfish.dev.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
    private final Map<String, Path> modules = moduleRoots();

    @Test
    void serviceDirectoriesExposeClearSubModules() {
        Map<String, Set<String>> expected = Map.of(
                "flyfish-auth", Set.of("flyfish-auth-api", "flyfish-auth-app"),
                "flyfish-lowcode", Set.of("flyfish-lowcode-api", "flyfish-lowcode-app"),
                "flyfish-shop", Set.of("flyfish-shop-api", "flyfish-shop-app")
        );

        Map<String, Set<String>> actual = new HashMap<>();
        expected.forEach((service, ignored) -> actual.put(service, childArtifactIds(root.resolve(service))));

        assertEquals(expected, actual);
    }

    @Test
    void internalMavenDependenciesFollowModuleBoundaries() throws Exception {
        Map<String, Set<String>> expected = Map.ofEntries(
                Map.entry("flyfish-portal-api", Set.of()),
                Map.entry("flyfish-common", Set.of("flyfish-ddl")),
                Map.entry("flyfish-auth-api", Set.of("flyfish-common")),
                Map.entry("flyfish-auth-app", Set.of("flyfish-common", "flyfish-auth-api")),
                Map.entry("flyfish-platform", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-auth-api")),
                Map.entry("flyfish-git", Set.of("flyfish-common")),
                Map.entry("flyfish-lowcode-api", Set.of()),
                Map.entry("flyfish-lowcode-app", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-platform", "flyfish-auth-api")),
                Map.entry("flyfish-shop-api", Set.of()),
                Map.entry("flyfish-shop-app", Set.of("flyfish-portal-api", "flyfish-common", "flyfish-platform", "flyfish-auth-api", "flyfish-git"))
        );

        Map<String, Set<String>> actual = new HashMap<>();
        for (String module : expected.keySet()) {
            actual.put(module, internalDependencies(pom(module)));
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
                "flyfish-auth-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user."
                ),
                "flyfish-lowcode-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "org.springframework."
                ),
                "flyfish-shop-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "org.springframework."
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
                "flyfish-lowcode-app", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support."
                ),
                "flyfish-shop-app", List.of(
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
            Path sourceRoot = moduleRoot(entry.getKey()).resolve("src/main/java");
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
                String owner = moduleId(file);
                String packageName = packageName(file);
                if (!owner.isBlank() && !packageName.isBlank()) {
                    packageOwners.computeIfAbsent(packageName, ignored -> new HashSet<>()).add(owner);
                }
            }
        }

        Set<String> applicationModules = Set.of("flyfish-auth-app", "flyfish-lowcode-app", "flyfish-shop-app");
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
                "flyfish-auth-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.generator.",
                        "group.flyfish.dev.git.",
                        "group.flyfish.dev.oauth.controller.",
                        "group.flyfish.dev.oauth.service.",
                        "group.flyfish.dev.oauth.vender.",
                        "group.flyfish.dev.shop.",
                        "group.flyfish.dev.support.",
                        "group.flyfish.dev.user.repository.",
                        "group.flyfish.dev.user.service.impl.",
                        "Repository",
                        "Service"
                ),
                "flyfish-lowcode-api", List.of(
                        "group.flyfish.dev.generator.",
                        "Repository",
                        "Service"
                ),
                "flyfish-shop-api", List.of(
                        "group.flyfish.dev.customer.",
                        "group.flyfish.dev.support.",
                        "Repository",
                        "Service"
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
            Path moduleRoot = moduleRoot(entry.getKey()).resolve("src/main");
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
                "flyfish-lowcode-app", List.of(
                        "飞鱼小铺",
                        "Shop",
                        "shop",
                        "/shop",
                        "/shops"
                ),
                "flyfish-shop-app", List.of(
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
            Path moduleRoot = moduleRoot(entry.getKey()).resolve("src/main");
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
                "flyfish-auth-app", List.of(
                        "flyfish-common.yml",
                        "flyfish-auth.yml"
                ),
                "flyfish-lowcode-app", List.of(
                        "flyfish-common.yml",
                        "flyfish-platform.yml",
                        "flyfish-auth-client.yml",
                        "flyfish-lowcode.yml"
                ),
                "flyfish-shop-app", List.of(
                        "flyfish-common.yml",
                        "flyfish-platform.yml",
                        "flyfish-auth-client.yml",
                        "flyfish-git.yml",
                        "flyfish-shop.yml"
                )
        );

        Map<String, List<String>> actual = new HashMap<>();
        for (String module : expected.keySet()) {
            Path application = moduleRoot(module).resolve("src/main/resources/application.yml");
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
                String owner = moduleId(file);
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
    void apiModulesDoNotCarryImplementationResources() throws Exception {
        List<String> apiModules = List.of("flyfish-auth-api", "flyfish-lowcode-api", "flyfish-shop-api");
        List<String> resources = new ArrayList<>();
        for (String module : apiModules) {
            Path moduleRoot = moduleRoot(module);
            Path sourceRoot = moduleRoot.resolve("src/main/resources");
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            try (var stream = Files.walk(sourceRoot)) {
                stream.filter(Files::isRegularFile)
                        .map(root::relativize)
                        .map(Path::toString)
                        .forEach(resources::add);
            }
        }

        assertTrue(resources.isEmpty(), () -> String.join(System.lineSeparator(), resources));
    }

    private Set<String> internalDependencies(Path pom) throws Exception {
        Document document = document(pom);
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

    private Set<String> childArtifactIds(Path serviceRoot) {
        Set<String> result = new HashSet<>();
        try (var stream = Files.list(serviceRoot)) {
            for (Path child : stream.filter(Files::isDirectory).toList()) {
                Path pom = child.resolve("pom.xml");
                if (Files.exists(pom)) {
                    result.add(artifactId(pom));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
                String owner = moduleId(file);
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

    private String moduleId(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return modules.entrySet().stream()
                .filter(entry -> normalized.startsWith(entry.getValue()))
                .max((left, right) -> Integer.compare(left.getValue().getNameCount(), right.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private Path moduleRoot(String artifactId) {
        Path path = modules.get(artifactId);
        if (path == null) {
            throw new IllegalArgumentException("Unknown module: " + artifactId);
        }
        return path;
    }

    private Path pom(String artifactId) {
        return moduleRoot(artifactId).resolve("pom.xml");
    }

    private Map<String, Path> moduleRoots() {
        Map<String, Path> result = new HashMap<>();
        try (var stream = Files.walk(root)) {
            for (Path pom : stream
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .toList()) {
                result.put(artifactId(pom), pom.getParent().toAbsolutePath().normalize());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private String artifactId(Path pom) {
        try {
            return text(document(pom), "artifactId");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    private Document document(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private String text(Document document, String tagName) {
        NodeList nodes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
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
        Path current = userDir;
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String source = Files.readString(pom, StandardCharsets.UTF_8);
                    if (source.contains("<artifactId>flyfish-dev</artifactId>")
                            && source.contains("<packaging>pom</packaging>")) {
                        return current;
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            current = current.getParent();
        }
        return userDir;
    }
}
