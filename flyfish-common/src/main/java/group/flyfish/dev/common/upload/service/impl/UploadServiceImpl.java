package group.flyfish.dev.common.upload.service.impl;

import group.flyfish.dev.common.upload.domain.FileMetadata;
import group.flyfish.dev.common.upload.repository.FileMetadataRepository;
import group.flyfish.dev.common.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.File;
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
        // 生成文件存储路径：data/images/年月/hash/原始文件名
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storePath = Paths.get(datePath, hash).toString();
        String fileName = safeFilename(file.filename());

        // 创建目录
        Path uploadPath = Paths.get(UPLOAD_DIR, storePath);
        File uploadDir = uploadPath.toFile();
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        // 保存文件
        Path filePath = uploadPath.resolve(fileName);

        return Mono.fromCallable(() -> {
                    if (!Files.exists(filePath)) {
                        Files.write(filePath, bytes);
                    }
                    return Files.size(filePath);
                })
                .map(size -> FileMetadata.builder()
                        .hash(hash)
                        .originalFilename(fileName)
                        .path(filePath.toString())
                        .size(size)
                        .contentType(file.headers().getContentType() == null
                                ? "application/octet-stream"
                                : file.headers().getContentType().toString())
                        .url(IMAGE_URL_PREFIX + "/" + storePath + "/" + fileName)
                        .build())
                .flatMap(fileMetadataRepository::save);
    }

    private String safeFilename(String filename) {
        String name = Paths.get(filename == null ? "attachment" : filename).getFileName().toString();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isBlank()) {
            return "attachment";
        }
        return name.length() > 120 ? name.substring(0, 120).toLowerCase(Locale.ROOT) : name;
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
