package group.flyfish.dev.common.upload.controller;

import group.flyfish.dev.annotations.user.CurrentUser;
import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.common.exception.BusinessException;
import group.flyfish.dev.common.upload.domain.vo.FileAttachmentVo;
import group.flyfish.dev.common.upload.service.UploadService;
import group.flyfish.dev.auth.api.user.PortalUserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/portal/files")
@RequiredArgsConstructor
public class PortalFileController {

    /**
     * 当前沟通类附件上限。保持轻量，避免聊天和工单被大文件拖慢。
     */
    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;

    private final UploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Result<FileAttachmentVo>> upload(@CurrentUser PortalUserVo user,
                                                 @RequestPart("file") FilePart file) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            return Mono.error(new BusinessException("USER_REQUIRED", "请先登录后再上传附件"));
        }
        Long contentLength = file.headers().getContentLength();
        if (contentLength != null && contentLength > MAX_ATTACHMENT_SIZE) {
            return Mono.error(new BusinessException("ATTACHMENT_TOO_LARGE", "附件不能超过 20MB"));
        }
        return uploadService.upload(file)
                .flatMap(metadata -> {
                    if (metadata.getSize() != null && metadata.getSize() > MAX_ATTACHMENT_SIZE) {
                        return Mono.error(new BusinessException("ATTACHMENT_TOO_LARGE", "附件不能超过 20MB"));
                    }
                    return Mono.just(Result.ok(FileAttachmentVo.from(metadata)));
                });
    }
}
