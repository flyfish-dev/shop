package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.service.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DeliveryTaskLedger {
    private final DatabaseClient db;
    public Mono<DeliveryResult> run(ShopOrder order, String task, Supplier<Mono<DeliveryResult>> action) {
        return run(order, task, false, action);
    }
    public Mono<DeliveryResult> run(ShopOrder order, String task, boolean retrySafe, Supplier<Mono<DeliveryResult>> action) {
        if (!"xianyu".equals(order.getPaymentProvider())) return Mono.defer(action);
        long now = Instant.now().getEpochSecond();
        String lease = java.util.UUID.randomUUID().toString();
        return db.sql("INSERT INTO shop_delivery_task (order_no,task_key,status,result_message,lease_until,updated_at,retry_safe) VALUES (:order,:task,'READY','',0,:now,:safe)")
                .bind("order", order.getOrderNo()).bind("task", task).bind("now", now).bind("safe",retrySafe).fetch().rowsUpdated()
                .onErrorResume(DuplicateKeyException.class, e -> Mono.just(0L))
                .then(db.sql("SELECT status,result_message FROM shop_delivery_task WHERE order_no=:order AND task_key=:task")
                        .bind("order", order.getOrderNo()).bind("task", task)
                        .map((r,m) -> new TaskState(r.get("status", String.class), r.get("result_message", String.class))).one())
                .flatMap(state -> {
                    if ("SUCCESS".equals(state.status)) return Mono.just(DeliveryResult.ok(state.message));
                    return db.sql("UPDATE shop_delivery_task SET status='RUNNING',lease_token=:lease,lease_until=:until,updated_at=:now WHERE order_no=:order AND task_key=:task AND status IN ('READY','FAILED') AND EXISTS (SELECT 1 FROM shop_order o WHERE o.order_no=:order AND COALESCE(o.refund_amount,0)=0 AND (o.refund_state IS NULL OR o.refund_state NOT IN ('PENDING','PROCESSING','PARTIAL','REFUNDED')))")
                            .bind("lease",lease).bind("until", now + 300).bind("now", now).bind("order", order.getOrderNo()).bind("task", task)
                            .fetch().rowsUpdated().flatMap(n -> n == 0
                                    ? Mono.just(DeliveryResult.failed("交付任务执行中或结果待核查"))
                                    : Mono.defer(action).timeout(java.time.Duration.ofSeconds(240))
                                    .onErrorResume(e -> Mono.just(DeliveryResult.failed("交付任务失败或结果待核查")))
                                    .flatMap(result -> db.sql("UPDATE shop_delivery_task SET status=:state,result_message=:message,lease_until=0,updated_at=:now WHERE order_no=:order AND task_key=:task AND status='RUNNING' AND lease_token=:lease")
                                            .bind("state", result.isSuccess() ? "SUCCESS" : retrySafe ? "FAILED" : "UNKNOWN")
                                            .bind("message", org.apache.commons.lang3.StringUtils.left(result.getMessage() == null ? "" : result.getMessage(), 500))
                                            .bind("now", Instant.now().getEpochSecond()).bind("order", order.getOrderNo()).bind("task", task).bind("lease",lease)
                                            .fetch().rowsUpdated().thenReturn(result)));
                });
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000)
    public Mono<Void> recoverInterruptedTasks() {
        return Mono.defer(() -> {
            long now=Instant.now().getEpochSecond();
            return db.sql("UPDATE shop_delivery_task SET status=CASE WHEN retry_safe THEN 'READY' ELSE 'UNKNOWN' END,lease_until=0,updated_at=:now WHERE status='RUNNING' AND lease_until<:now")
                    .bind("now",now).fetch().rowsUpdated()
                    .then(db.sql("UPDATE shop_order SET delivery_status='FAILED',delivery_message='交付中断，等待恢复' WHERE payment_provider='xianyu' AND delivery_status='PROCESSING' AND update_time<:cutoff AND NOT EXISTS (SELECT 1 FROM shop_delivery_task t WHERE t.order_no=shop_order.order_no AND t.status='RUNNING' AND t.lease_until>:now)")
                            .bind("now",now).bind("cutoff",java.time.LocalDateTime.now().minusMinutes(5)).fetch().rowsUpdated()).then();
        });
    }
    public reactor.core.publisher.Flux<java.util.Map<String,Object>> tasks(String orderNo) {
        return db.sql("SELECT task_key,status,result_message,retry_safe FROM shop_delivery_task WHERE order_no=:no ORDER BY task_key")
                .bind("no",orderNo).map((r,m) -> java.util.Map.<String,Object>of("taskKey",r.get("task_key",String.class),
                        "status",r.get("status",String.class),"message",r.get("result_message",String.class),
                        "retrySafe",r.get("retry_safe",Boolean.class))).all();
    }
    @org.springframework.transaction.annotation.Transactional
    public Mono<Boolean> resolve(String orderNo,String task,String actor,String resolution,String evidence) {
        if (!java.util.Set.of("SUCCESS","READY").contains(resolution) || evidence==null || evidence.trim().length()<12 || evidence.length()>1000)
            return Mono.error(new IllegalArgumentException("请记录远端核查依据后再处理交付任务"));
        return db.sql("UPDATE shop_delivery_task SET status=:state,result_message='已人工核查',updated_at=:now WHERE order_no=:no AND task_key=:task AND status='UNKNOWN'")
                .bind("state",resolution).bind("now",Instant.now().getEpochSecond()).bind("no",orderNo).bind("task",task).fetch().rowsUpdated()
                .flatMap(n -> n==1 ? db.sql("INSERT INTO shop_delivery_resolution VALUES (:id,:no,:task,:actor,:state,:evidence,:now)")
                        .bind("id",java.util.UUID.randomUUID().toString()).bind("no",orderNo).bind("task",task).bind("actor",actor)
                        .bind("state",resolution).bind("evidence",evidence.trim()).bind("now",Instant.now().getEpochSecond())
                        .fetch().rowsUpdated().thenReturn(true) : Mono.just(false));
    }
    private record TaskState(String status, String message) { }
}
