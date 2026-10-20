package group.flyfish.dev.shop.xianyu;

import group.flyfish.dev.shop.domain.po.ShopOrder;
import group.flyfish.dev.shop.repository.ShopOrderRepository;
import group.flyfish.dev.shop.service.DeliveryResult;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class XianyuOrderConsistencyTest {
    private DatabaseClient db;
    private DeliveryTaskLedger ledger;
    @BeforeEach void setup() {
        String url=System.getenv("XIANYU_TEST_DATABASE_URL");
        if(url==null) url="r2dbc:h2:mem:///xy_shop_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var options=ConnectionFactoryOptions.parse(url).mutate();
        if(System.getenv("XIANYU_TEST_DATABASE_USER")!=null) options.option(ConnectionFactoryOptions.USER,System.getenv("XIANYU_TEST_DATABASE_USER"));
        if(System.getenv("XIANYU_TEST_DATABASE_PASSWORD")!=null) options.option(ConnectionFactoryOptions.PASSWORD,System.getenv("XIANYU_TEST_DATABASE_PASSWORD"));
        var factory=ConnectionFactories.get(options.build());db=DatabaseClient.create(factory);
        db.sql("""
            CREATE TABLE IF NOT EXISTS shop_order (
              order_no VARCHAR(80) PRIMARY KEY,payment_provider VARCHAR(30),status VARCHAR(30),amount DECIMAL(18,2),
              currency VARCHAR(8),paid_time TIMESTAMP NULL,delivery_status VARCHAR(30),transaction_code VARCHAR(100),
              expire_time TIMESTAMP NULL,update_time TIMESTAMP NULL,delivery_message VARCHAR(500),is_delete BOOLEAN DEFAULT FALSE)
            """).fetch().rowsUpdated().block();
        new ResourceDatabasePopulator(new ClassPathResource("schema/49-shop-xianyu.sql")).populate(factory).block();
        for(String table:java.util.List.of("shop_delivery_resolution","shop_delivery_task","shop_order")) db.sql("DELETE FROM "+table).fetch().rowsUpdated().block();
        db.sql("INSERT INTO shop_order (order_no,payment_provider,status,amount,currency,delivery_status) VALUES ('order','xianyu','PAYING',100.00,'CNY','WAITING')").fetch().rowsUpdated().block();
        ledger=new DeliveryTaskLedger(db);
    }
    private String query(String method,Class<?>... parameters) throws Exception {
        return ShopOrderRepository.class.getMethod(method,parameters).getAnnotation(Query.class).value();
    }
    @Test void concurrentPaymentConfirmationHasOneWinnerAndWrongAmountsAreRejected() throws Exception {
        String sql=query("confirmXianyuPaid",String.class,BigDecimal.class,String.class);
        long winners=Flux.range(0,20).flatMap(i->db.sql(sql).bind("orderNo","order").bind("amount",new BigDecimal("100.00"))
                .bind("transaction","XY-order").fetch().rowsUpdated(),20).reduce(0L,Long::sum).block();
        assertEquals(1,winners);
        assertEquals(0,db.sql(sql).bind("orderNo","order").bind("amount",new BigDecimal("99.99")).bind("transaction","wrong").fetch().rowsUpdated().block());
    }
    @Test void refundedOrderCannotClaimNewDeliveryAndClosedOrderCanReconcileLatePayment() throws Exception {
        db.sql("UPDATE shop_order SET status='CLOSED'").fetch().rowsUpdated().block();
        assertEquals(1,db.sql(query("confirmXianyuPaid",String.class,BigDecimal.class,String.class)).bind("orderNo","order")
                .bind("amount",new BigDecimal("100.00")).bind("transaction","late-payment").fetch().rowsUpdated().block());
        db.sql("UPDATE shop_order SET refund_state='PARTIAL'").fetch().rowsUpdated().block();
        assertEquals(0,db.sql(query("claimXianyuDelivery",String.class)).bind("orderNo","order").fetch().rowsUpdated().block());
    }
    @Test void successfulStepsAreNotRepeatedAndUncertainGrantsNeedManualResolution() {
        ShopOrder order=new ShopOrder();order.setOrderNo("order");order.setPaymentProvider("xianyu");
        AtomicInteger successful=new AtomicInteger(),failed=new AtomicInteger();
        ledger.run(order,"download",true,()->{successful.incrementAndGet();return Mono.just(DeliveryResult.ok("ready"));}).block();
        ledger.run(order,"repository",()->{failed.incrementAndGet();return Mono.just(DeliveryResult.failed("timeout"));}).block();
        ledger.run(order,"download",true,()->{successful.incrementAndGet();return Mono.just(DeliveryResult.ok("ready"));}).block();
        ledger.run(order,"repository",()->{failed.incrementAndGet();return Mono.just(DeliveryResult.ok("grant"));}).block();
        assertEquals(1,successful.get());assertEquals(1,failed.get());
        assertEquals("UNKNOWN",ledger.tasks("order").filter(t->t.get("taskKey").equals("repository")).blockFirst().get("status"));
        assertTrue(ledger.resolve("order","repository","1","SUCCESS","Remote collaborator list confirms the intended recipient has access.").block());
        assertTrue(ledger.run(order,"repository",()->Mono.error(new AssertionError("must not execute"))).block().isSuccess());
    }
    @Test void expiredLeasesRecoverOnlyDeclaredSafeStepsAndRefundFreezesNewSteps() {
        db.sql("INSERT INTO shop_delivery_task (order_no,task_key,status,result_message,lease_until,updated_at,retry_safe) VALUES ('order','safe','RUNNING','',0,0,TRUE),('order','unsafe','RUNNING','',0,0,FALSE)")
                .fetch().rowsUpdated().block();ledger.recoverInterruptedTasks().block();
        var states=ledger.tasks("order").collectMap(t->t.get("taskKey"),t->t.get("status")).block();
        assertEquals("READY",states.get("safe"));assertEquals("UNKNOWN",states.get("unsafe"));
        db.sql("UPDATE shop_order SET refund_state='PROCESSING'").fetch().rowsUpdated().block();
        ShopOrder order=new ShopOrder();order.setOrderNo("order");order.setPaymentProvider("xianyu");
        assertFalse(ledger.run(order,"safe",true,()->Mono.error(new AssertionError("refund must freeze delivery"))).block().isSuccess());
    }
    @Test void deliveryCompletionCannotOverwriteConcurrentRefundAndManualCompletionIsFrozen() throws Exception {
        db.sql("UPDATE shop_order SET paid_time=CURRENT_TIMESTAMP,refund_state='PROCESSING',refund_amount=1.00").fetch().rowsUpdated().block();
        String sql=query("updateXianyuDelivery",String.class,String.class,String.class,String.class,boolean.class);
        assertEquals(1,db.sql(sql).bind("orderNo","order").bind("state","DELIVERED").bind("delivery","SUCCESS")
                .bind("message","Completed before refund freeze").bind("requireActive",false).fetch().rowsUpdated().block());
        assertEquals("PROCESSING",db.sql("SELECT refund_state FROM shop_order WHERE order_no='order'").map((r,m)->r.get("refund_state",String.class)).one().block());
        assertEquals(0,db.sql(sql).bind("orderNo","order").bind("state","DELIVERED").bind("delivery","SUCCESS")
                .bind("message","Manual delivery").bind("requireActive",true).fetch().rowsUpdated().block());
        db.sql("UPDATE shop_order SET delivery_status='FAILED',refund_state='CLOSED'").fetch().rowsUpdated().block();
        assertEquals(0,db.sql(query("claimXianyuDelivery",String.class)).bind("orderNo","order").fetch().rowsUpdated().block());
        db.sql("UPDATE shop_order SET channel_order_key='channel',channel_refund_revision=0").fetch().rowsUpdated().block();
        String projection=query("projectXianyuRefund",String.class,String.class,String.class,BigDecimal.class,long.class);
        for(var event:java.util.List.of(new Object[]{5L,"PARTIAL",BigDecimal.ONE},new Object[]{4L,"CLOSED",BigDecimal.ZERO}))
            db.sql(projection).bind("orderNo","order").bind("key","channel").bind("revision",event[0])
                    .bind("state",event[1]).bind("amount",event[2]).fetch().rowsUpdated().block();
        assertEquals("PARTIAL",db.sql("SELECT refund_state FROM shop_order WHERE order_no='order'").map((r,m)->r.get("refund_state",String.class)).one().block());
        assertEquals(0,BigDecimal.ONE.compareTo(db.sql("SELECT refund_amount FROM shop_order WHERE order_no='order'").map((r,m)->r.get("refund_amount",BigDecimal.class)).one().block()));
        for(String state:java.util.List.of("PROCESSING","CLOSED")) db.sql(projection).bind("orderNo","order").bind("key","channel")
                .bind("revision",6L).bind("state",state).bind("amount",BigDecimal.ZERO).fetch().rowsUpdated().block();
        assertEquals("PROCESSING",db.sql("SELECT refund_state FROM shop_order WHERE order_no='order'").map((r,m)->r.get("refund_state",String.class)).one().block());
    }
}
