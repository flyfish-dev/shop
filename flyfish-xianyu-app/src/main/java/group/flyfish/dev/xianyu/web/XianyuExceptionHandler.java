package group.flyfish.dev.xianyu.web;

import group.flyfish.dev.common.bean.Result;
import group.flyfish.dev.xianyu.security.XianyuIdentityService.SessionExpired;
import group.flyfish.dev.xianyu.top.TopGateway.TopFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages="group.flyfish.dev.xianyu")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XianyuExceptionHandler {
    @ExceptionHandler(SessionExpired.class)
    public ResponseEntity<Result<Object>> expired() {
        return ResponseEntity.status(401).body(Result.error(401,"登录已过期，请重新授权"));
    }
    @ExceptionHandler(TopFailure.class)
    public ResponseEntity<Result<Object>> platform(TopFailure error) {
        return ResponseEntity.status(502).body(Result.error(502,error.indeterminate ? "平台处理结果待核查，请查看订单状态" : "平台请求未通过，请核对渠道授权"));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Object>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Result.error(400,error.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Object>> unavailable() {
        return ResponseEntity.status(503).body(Result.error(503,"渠道服务暂不可用，请稍后查看订单"));
    }
}
