package group.flyfish.dev.common.upload.service;

import group.flyfish.dev.common.upload.domain.FileMetadata;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * 上传服务
 *
 * @author wangyu
 */
public interface UploadService {

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件元数据
     */
    Mono<FileMetadata> upload(FilePart file);
}