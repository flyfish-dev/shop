package group.flyfish.dev.common.upload.repository;

import group.flyfish.dev.common.repository.DefaultReactiveRepository;
import group.flyfish.dev.common.upload.domain.FileMetadata;
import reactor.core.publisher.Mono;

/**
 * 文件元数据仓库
 *
 * @author wangyu
 */
public interface FileMetadataRepository extends DefaultReactiveRepository<FileMetadata> {

    /**
     * 根据哈希值查找文件元数据
     *
     * @param hash 文件哈希值
     * @return 文件元数据
     */
    default Mono<FileMetadata> findByHash(String hash) {
        return findOneBy("hash", hash);
    }
}
