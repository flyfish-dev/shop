package group.flyfish.dev.common.upload.service.impl;

import group.flyfish.dev.common.upload.domain.FileMetadata;
import group.flyfish.dev.common.upload.repository.FileMetadataRepository;
import group.flyfish.dev.common.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 上传服务实现
 *
 * @author wangyu
 */
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final FileMetadataRepository fileMetadataRepository;
    private static final String UPLOAD_DIR = "data/images";
    private static final String IMAGE_URL_PREFIX = "/images";

    @Override
    public Mono<FileMetadata> upload(FilePart file) {
        return fileBytes(file)
                .flatMap(bytes -> {
                    String hash = calculateHash(bytes);
                    return fileMetadataRepository.findByHash(hash)
                            .switchIfEmpty(saveFile(file, hash, bytes));
                });
    }

    private Mono<byte[]> fileBytes(FilePart file) {
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } catch (Exception e) {
                        throw new RuntimeException("读取上传文件失败", e);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    private String calculateHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("计算文件哈希值失败", e);
        }
    }

    private Mono<FileMetadata> saveFile(FilePart file, String hash, byte[] bytes) {
        // 用户可见文件名允许中文；磁盘与 URL 只使用 ASCII，避免 native/systemd 环境字符集导致路径编码失败。
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storeUrlPath = datePath + "/" + hash;
        String displayFilename = safeFilename(file.filename());
        String storageFilename = storageFilename(hash, displayFilename);
        Path uploadPath = Paths.get(UPLOAD_DIR, datePath, hash);
        Path filePath = uploadPath.resolve(storageFilename);

        return Mono.fromCallable(() -> {
                    Files.createDirectories(uploadPath);
                    if (!Files.exists(filePath)) {
                        Files.write(filePath, bytes);
                    }
                    return Files.size(filePath);
                })
                .map(size -> FileMetadata.builder()
                        .hash(hash)
                        .originalFilename(displayFilename)
                        .path(filePath.toString())
                        .size(size)
                        .contentType(file.headers().getContentType() == null
                                ? "application/octet-stream"
                                : file.headers().getContentType().toString())
                        .url(IMAGE_URL_PREFIX + "/" + storeUrlPath + "/" + storageFilename)
                        .build())
                .flatMap(fileMetadataRepository::save);
    }

    static String safeFilename(String filename) {
        String rawName = filename == null ? "attachment" : filename;
        String normalized = rawName.replace('\\', '/');
        int lastSeparator = normalized.lastIndexOf('/');
        String name = lastSeparator >= 0 ? normalized.substring(lastSeparator + 1) : normalized;
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isBlank()) {
            return "attachment";
        }
        return limitFilename(name);
    }

    static String storageFilename(String hash, String displayFilename) {
        return hash + extensionOf(displayFilename);
    }

    private static String limitFilename(String name) {
        if (name.length() <= 120) {
            return name;
        }
        String extension = extensionOf(name);
        int maxNameLength = 120 - extension.length();
        return name.substring(0, Math.max(1, maxNameLength)) + extension;
    }

    private static String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!extension.matches("[a-z0-9]{1,16}")) {
            return "";
        }
        return "." + extension;
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
