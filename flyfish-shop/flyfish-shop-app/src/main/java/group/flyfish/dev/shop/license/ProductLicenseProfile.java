package group.flyfish.dev.shop.license;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 飞鱼产品授权的稳定定义。
 *
 * <p>产品编码、允许功能、运行授权格式和下载文件前缀集中在这里维护，避免管理端、
 * 签发端和订单下载分别写一套字符串规则。新增产品必须先在这里登记，再进入商品配置。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ProductLicenseProfile {

    OFFICE_PREVIEW(
            "office-preview",
            "Office Preview",
            "office-preview",
            "office-preview-license-v2",
            2,
            List.of("doc", "docx", "ppt", "pptx", "xls", "xlsx", "xlsb", "virtual-excel")
    ),
    DWG_VIEWER(
            "dwg-viewer",
            "DWG Viewer",
            "dwg-viewer",
            "flyfish-product-license-v1",
            1,
            List.of("dwg")
    ),
    MODEL_VIEWER(
            "model-viewer",
            "Flyfish Model Viewer",
            "model-viewer",
            "flyfish-product-license-v1",
            1,
            List.of(
                    "model-preview", "gltf", "step", "obj", "fbx", "stl", "ply", "dae", "3ds", "3mf",
                    "amf", "usd", "vrml", "vtk", "pcd", "xyz", "vox", "lwo", "kmz", "gcode", "md2",
                    "bvh", "pdb", "ldraw", "draco", "nrrd", "rhino3dm"
            )
    ),
    FILE_VIEWER(
            "file-viewer",
            "Flyfish Viewer",
            "flyfish-viewer-brand-removal-statement",
            "flyfish-viewer-brand-removal-statement-v1",
            1,
            List.of("visible-branding-removal", "apache-2.0-attribution-required")
    );

    private final String code;
    private final String displayName;
    private final String fileStem;
    private final String runtimeEnvelopeFormat;
    private final int payloadVersion;
    private final List<String> features;

    public static ProductLicenseProfile fromCode(String value) {
        String code = StringUtils.lowerCase(StringUtils.trimToNull(value), Locale.ROOT);
        return Arrays.stream(values())
                .filter(profile -> profile.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static ProductLicenseProfile fromCodeOrDefault(String value) {
        ProductLicenseProfile profile = fromCode(value);
        return profile == null ? OFFICE_PREVIEW : profile;
    }
}
