package group.flyfish.dev.common.exception;

import group.flyfish.dev.common.bean.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author wangyu
 * @Date 2019
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理自定义异常
     */
    @ResponseBody
    @ExceptionHandler(ServiceException.class)
    public Result<?> handleRRException(ServiceException e) {
        log.error(e.getMessage(), e);
        return Result.error(e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public Result<?> validException(MethodArgumentNotValidException e) {
        return Result.error(e.getBindingResult().getFieldErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("，")));
    }

    @ResponseBody
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error(e.getMessage(), e);
        return Result.error("操作失败，" + e.getMessage());
    }

    /**
     * spring默认上传大小100MB 超出大小捕获异常MaxUploadSizeExceededException
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public Result<?> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error(e.getMessage(), e);
        return Result.error("文件大小超出10MB限制, 请压缩或降低文件质量! ");
    }

}
