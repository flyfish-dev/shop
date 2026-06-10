package group.flyfish.dev.generator.utils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件压缩工具类
 *
 * @author wangyu
 */
@Slf4j
public class FileZipUtils {

    private static void zipFile(ZipOutputStream zos, Path path) {
        // 以只读模式打开
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            ZipEntry zipEntry = new ZipEntry(path.toString());
            zos.putNextEntry(zipEntry);
            StreamUtils.copy(in, zos);
            zos.closeEntry();
        } catch (IOException e) {
            log.error("添加文件到压缩包失败！" + e.getMessage());
        }
    }

    /**
     * 递归压缩目录结构
     *
     * @param zos  输出流
     * @param path 文件
     */
    private static void directory(ZipOutputStream zos, Path path) {
        try (Stream<Path> stream = Files.list(path)) {
            stream.forEachOrdered(child -> {
                if (Files.isDirectory(child)) {
                    directory(zos, child);
                } else {
                    zipFile(zos, child);
                }
            });
        } catch (IOException e) {
            log.error("读取文件目录失败！" + e.getMessage());
        }
    }

    /**
     * 压缩文件目录
     *
     * @param source 源文件目录（单个文件和多层目录）
     * @param zos    zip输出流
     */
    @SneakyThrows
    public static void zipFiles(String source, ZipOutputStream zos) {
        Path path = Paths.get(source);
        try {
            if (Files.isDirectory(path)) {
                directory(zos, path);
            } else {
                zipFile(zos, path);
            }
        } finally {
            FileSystemUtils.deleteRecursively(path);
        }
    }
}
