package group.flyfish.dev.shop.service.support;

import group.flyfish.dev.common.json.JacksonUtils;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.shop.domain.dto.ShopContractI18nDto;
import group.flyfish.dev.shop.domain.po.ShopContract;
import group.flyfish.dev.shop.domain.po.ShopContractFile;
import group.flyfish.dev.shop.domain.vo.ShopContractI18nVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合同语言、逻辑文档和版本选择规则。
 *
 * <p>一个法律文档可以存在多个语言版本，但购买者在一次签署流程中只需要阅读与当前界面语言
 * 最匹配的一份。文件版本以逻辑文档为单位推进，避免合同更新后继续复用旧版本签署记录。</p>
 */
@Component
public class ShopContractLocalization {

    public static final String DEFAULT_LOCALE = "zh-CN";

    public static final String ENGLISH_LOCALE = "en-US";

    private static final Comparator<ShopContractFile> FILE_ORDER = Comparator
            .comparing(ShopContractFile::getSort, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ShopContractFile::getId, Comparator.nullsLast(Long::compareTo));

    public String normalizeLocale(String locale) {
        String normalized = StringUtils.trimToEmpty(locale).replace('_', '-').toLowerCase(Locale.ROOT);
        return normalized.startsWith("en") ? ENGLISH_LOCALE : DEFAULT_LOCALE;
    }

    public String serialize(Map<String, ShopContractI18nDto> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        Map<String, ShopContractI18nDto> cleaned = new LinkedHashMap<>();
        value.forEach((locale, source) -> {
            ShopContractI18nDto normalized = normalize(source);
            if (normalized != null) {
                cleaned.put(normalizeLocale(locale), normalized);
            }
        });
        return cleaned.isEmpty() ? null : JacksonUtils.toJson(cleaned);
    }

    public Map<String, ShopContractI18nVo> deserialize(String value) {
        if (StringUtils.isBlank(value)) {
            return Map.of();
        }
        return JacksonUtils.readValue(value, new TypeReference<>() {
        });
    }

    public LocalizedContract localize(ShopContract contract, String locale) {
        String normalizedLocale = normalizeLocale(locale);
        ShopContractI18nVo localized = deserialize(contract.getI18n()).get(normalizedLocale);
        return new LocalizedContract(
                StringUtils.defaultIfBlank(localized == null ? null : localized.getName(), contract.getName()),
                StringUtils.defaultIfBlank(localized == null ? null : localized.getDescription(),
                        contract.getDescription()),
                localized == null || localized.getTags() == null || localized.getTags().isEmpty()
                        ? splitTags(contract.getTags()) : localized.getTags(),
                typeName(contract.getType(), normalizedLocale),
                normalizedLocale
        );
    }

    /**
     * 返回每个逻辑文档当前最新版本的全部语言变体。
     */
    public List<ShopContractFile> currentVariants(Collection<ShopContractFile> files) {
        Map<String, List<ShopContractFile>> grouped = enabledFiles(files).stream()
                .sorted(FILE_ORDER)
                .collect(Collectors.groupingBy(this::documentKey, LinkedHashMap::new, Collectors.toList()));
        List<ShopContractFile> result = new ArrayList<>();
        grouped.values().forEach(group -> {
            int currentVersion = group.stream().mapToInt(this::fileVersion).max().orElse(1);
            group.stream().filter(file -> fileVersion(file) == currentVersion).forEach(result::add);
        });
        return result;
    }

    /**
     * 为每个逻辑文档选择一个最合适的语言版本。
     */
    public List<ShopContractFile> selectLocaleVariants(Collection<ShopContractFile> files, String locale) {
        String requested = normalizeLocale(locale);
        Map<String, List<ShopContractFile>> grouped = currentVariants(files).stream()
                .collect(Collectors.groupingBy(this::documentKey, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .map(group -> selectVariant(group, requested))
                .flatMap(Optional::stream)
                .toList();
    }

    public String documentKey(ShopContractFile file) {
        if (StringUtils.isNotBlank(file.getDocumentKey())) {
            return file.getDocumentKey();
        }
        return "legacy-" + Objects.toString(file.getId(), "unsaved");
    }

    public String normalizeDocumentKey(String requested, String fileName) {
        String source = StringUtils.defaultIfBlank(requested, StringUtils.substringBeforeLast(fileName, "."));
        String slug = StringUtils.trimToEmpty(source)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (StringUtils.isNotBlank(requested) && StringUtils.isBlank(slug)) {
            throw new BusinessException("INVALID_CONTRACT_DOCUMENT_KEY",
                    "文档分组标识只能包含字母、数字、点、下划线和短横线");
        }
        if (StringUtils.isBlank(slug)) {
            slug = "document";
        }
        if (StringUtils.isBlank(requested)) {
            String suffix = Integer.toUnsignedString(Objects.toString(fileName, "document").hashCode(), 36);
            slug = StringUtils.left(slug, Math.max(1, 63 - suffix.length())) + "-" + suffix;
        }
        return StringUtils.left(slug, 64);
    }

    public int fileVersion(ShopContractFile file) {
        return file.getFileVersion() == null || file.getFileVersion() < 1 ? 1 : file.getFileVersion();
    }

    public int nextVersion(Collection<ShopContractFile> files, String documentKey, String locale) {
        List<ShopContractFile> group = enabledFiles(files).stream()
                .filter(file -> Objects.equals(documentKey, documentKey(file)))
                .toList();
        int currentVersion = group.stream().mapToInt(this::fileVersion).max().orElse(1);
        boolean localeAlreadyExists = group.stream()
                .filter(file -> fileVersion(file) == currentVersion)
                .map(ShopContractFile::getLocale)
                .map(this::normalizeLocale)
                .anyMatch(normalizeLocale(locale)::equals);
        return group.isEmpty() ? 1 : localeAlreadyExists ? currentVersion + 1 : currentVersion;
    }

    private List<ShopContractFile> enabledFiles(Collection<ShopContractFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(file -> Boolean.TRUE.equals(file.getEnabled())).toList();
    }

    private Optional<ShopContractFile> selectVariant(List<ShopContractFile> files, String requested) {
        Map<String, ShopContractFile> byLocale = files.stream()
                .sorted(FILE_ORDER)
                .collect(Collectors.toMap(
                        file -> normalizeLocale(file.getLocale()),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        return Optional.ofNullable(byLocale.get(requested))
                .or(() -> Optional.ofNullable(byLocale.get(DEFAULT_LOCALE)))
                .or(() -> files.stream().sorted(FILE_ORDER).findFirst());
    }

    private ShopContractI18nDto normalize(ShopContractI18nDto source) {
        if (source == null) {
            return null;
        }
        ShopContractI18nDto value = new ShopContractI18nDto();
        value.setName(StringUtils.trimToNull(source.getName()));
        value.setDescription(StringUtils.trimToNull(source.getDescription()));
        value.setTags(Optional.ofNullable(source.getTags()).orElse(List.of()).stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return value.getName() == null && value.getDescription() == null && value.getTags().isEmpty() ? null : value;
    }

    private List<String> splitTags(String tags) {
        if (StringUtils.isBlank(tags)) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private String typeName(ShopContract.Type type, String locale) {
        if (type == null) {
            return null;
        }
        if (!ENGLISH_LOCALE.equals(locale)) {
            return type.getTitle();
        }
        return switch (type) {
            case PURCHASE_AGREEMENT -> "Purchase Agreement";
            case SOFTWARE_LICENSE -> "Software License Agreement";
            case SERVICE_AGREEMENT -> "Service Agreement";
            case SUBSCRIPTION_AGREEMENT -> "Subscription Agreement";
            case CONFIDENTIALITY_AGREEMENT -> "Confidentiality Agreement";
            case DATA_PROCESSING_AGREEMENT -> "Data Processing Agreement";
            case ACCEPTANCE_CONFIRMATION -> "Acceptance Confirmation";
            case CUSTOM -> "Other Agreement";
        };
    }

    public record LocalizedContract(String name, String description, List<String> tags,
                                    String typeName, String locale) {
    }
}
