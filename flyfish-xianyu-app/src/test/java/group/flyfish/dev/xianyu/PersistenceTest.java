package group.flyfish.dev.xianyu;

import group.flyfish.dev.common.bridge.BridgeSignature;
import group.flyfish.dev.common.bridge.*;
import group.flyfish.dev.xianyu.store.*;
import group.flyfish.dev.xianyu.store.XianyuModels.*;
import io.r2dbc.spi.ConnectionFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    private XianyuStore store;
    private DatabaseClient database;
    private io.r2dbc.spi.ConnectionFactory factory;
    @BeforeEach void setup() {
        String uri=System.getenv("XIANYU_TEST_DATABASE_URL");
        if(uri==null) uri="r2dbc:h2:mem:///xy_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var options=io.r2dbc.spi.ConnectionFactoryOptions.parse(uri).mutate();
        if(System.getenv("XIANYU_TEST_DATABASE_USER")!=null) options.option(io.r2dbc.spi.ConnectionFactoryOptions.USER,System.getenv("XIANYU_TEST_DATABASE_USER"));
        if(System.getenv("XIANYU_TEST_DATABASE_PASSWORD")!=null) options.option(io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD,System.getenv("XIANYU_TEST_DATABASE_PASSWORD"));
        factory=ConnectionFactories.get(options.build());
        new ResourceDatabasePopulator(new ClassPathResource("schema/38-xianyu-bridge.sql"),new ClassPathResource("schema/50-xianyu.sql")).populate(factory).block();
        database=DatabaseClient.create(factory);store=new XianyuStore(database,JsonMapper.builder().build());
        for(String table:List.of("xy_job","xy_order","xy_message","xy_listing_revision","xy_listing","xianyu_bridge_nonce")) database.sql("DELETE FROM "+table).fetch().rowsUpdated().block();
    }
    @Test void onlyOneConcurrentWorkerClaimsAJob() {
        store.enqueue("job","seller","ORDER_QUERY",Map.of("orderKey","order")).block();
        Job job=store.ready().blockFirst();
        List<Job> winners=Flux.range(0,20).flatMap(i->store.claim(job),20).collectList().block();
        assertEquals(1,winners.size());
        store.finish(winners.getFirst(),"SUCCESS",null).block();assertEquals("SUCCESS",store.jobs().blockFirst().status());
    }
    @Test void inboxDeduplicatesAndOrderVersionsPreventLostUpdates() {
        assertTrue(store.receive("123","s","topic","{}").block());assertFalse(store.receive("123","s","topic","{}").block());
        Order original=new Order("k","s","local","3318740388015865620","buyer","XIANYU_MINIAPP",100,"CREATED",2,"NONE",0,"WAITING","WAITING",Map.of(),0,store.now()+900);
        assertTrue(store.saveOrder(original,true).block());Order current=store.order("k").block();
        long winners=Flux.range(0,20).flatMap(i->store.saveOrder(current,false),20).filter(Boolean::booleanValue).count().block();
        assertEquals(1,winners);assertEquals("3318740388015865620",store.order("k").block().remoteOrderId());
    }
    @Test void crashRecoveryNeverRepeatsAnUnconfirmedCreation() {
        store.enqueue("create","s","ORDER_CREATE",Map.of()).block();store.enqueue("query","s","ORDER_QUERY",Map.of()).block();
        store.ready().concatMap(store::claim).collectList().block();
        database.sql("UPDATE xy_job SET lease_until=0").fetch().rowsUpdated().block();store.recoverLeases().block();
        var states=store.jobs().collectMap(Job::key,Job::status).block();assertEquals("UNKNOWN",states.get("create"));assertEquals("READY",states.get("query"));
    }
    @Test void bridgeReplayProtectionSurvivesServiceReconstruction() {
        XianyuBridgeProperties properties=new XianyuBridgeProperties();properties.setEnabled(true);properties.setDataFlowApproved(true);properties.setSecret("local-test-key-012345678901234567890");
        var ticket=BridgeSignature.sign("/internal/xianyu/shop/paid","{}",properties.getSecret());
        assertEquals("{}",new XianyuBridgeGuard(properties,database).accept(ticket,ticket.path()).block());
        assertThrows(RuntimeException.class,()->new XianyuBridgeGuard(properties,database).accept(ticket,ticket.path()).block());
    }
    @Test void capabilitiesAreUpsertedAtomicallyAndCatalogIsExplicitlyOptedIn() {
        var capability=new Capability("s","ITEM_QUERY",true,false,store.now()+3600,"param",List.of(),List.of("1"),"commerce");
        var transaction=org.springframework.transaction.reactive.TransactionalOperator.create(new org.springframework.r2dbc.connection.R2dbcTransactionManager(factory));
        Flux.range(0,20).flatMap(i->store.saveCapability(capability).as(transaction::transactional)
                .retryWhen(reactor.util.retry.Retry.backoff(4,java.time.Duration.ofMillis(30))
                        .filter(org.springframework.dao.TransientDataAccessException.class::isInstance)),20).then().block();
        assertEquals("param",store.capability("s","ITEM_QUERY","commerce").block().requestKey());
        assertNull(store.capability("s","ITEM_QUERY","buyer").block());
        XianyuProperties properties=new XianyuProperties();properties.setMiniappSeller("s");
        var catalog=new group.flyfish.dev.xianyu.trade.ChannelCatalogService(store,properties,null,JsonMapper.builder().build());
        Map<String,Object> payload=new java.util.LinkedHashMap<>();payload.put("miniapp_enabled",true);payload.put("single_sku_id","11");
        store.saveListing(new Listing("listing","s","1",null,0,"DRAFT",false,payload,Map.of()),true).block();
        assertDoesNotThrow(()->catalog.requireSelection("1","11").block());
        assertThrows(IllegalArgumentException.class,()->catalog.requireSelection("1","12").block());
        assertThrows(IllegalArgumentException.class,()->catalog.requireSelection("2","11").block());
        payload.put("miniapp_enabled",false);
        Listing current=store.listing("listing").block();
        store.saveListing(new Listing(current.key(),"s","1",null,current.revision(),"DRAFT",false,payload,Map.of()),false).block();
        assertThrows(IllegalArgumentException.class,()->catalog.requireSelection("1","11").block());
    }
    @Test void partialRefundsAreDurableAndDuplicateRequestDoesNotRefreezeOrRepeatRefund() {
        var top=org.mockito.Mockito.mock(group.flyfish.dev.xianyu.top.TopGateway.class);
        var bridge=org.mockito.Mockito.mock(group.flyfish.dev.xianyu.bridge.ApprovedBridgeClient.class);
        var mapper=JsonMapper.builder().build();
        org.mockito.Mockito.when(top.require(org.mockito.ArgumentMatchers.eq("s"),org.mockito.ArgumentMatchers.any()))
                .thenReturn(reactor.core.publisher.Mono.just(new Capability("s","PART_REFUND",true,true,store.now()+3600,"top_part_refund_param",List.of(),List.of(),"commerce")));
        org.mockito.Mockito.when(bridge.shop(org.mockito.ArgumentMatchers.eq("refund"),org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(reactor.core.publisher.Mono.just(mapper.readTree("1")));
        org.mockito.Mockito.when(top.call(org.mockito.ArgumentMatchers.eq("s"),org.mockito.ArgumentMatchers.eq(group.flyfish.dev.xianyu.top.TopOperation.PART_REFUND),org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(reactor.core.publisher.Mono.just(mapper.readTree("{\"module\":true}")));
        var transaction=org.springframework.transaction.reactive.TransactionalOperator.create(new org.springframework.r2dbc.connection.R2dbcTransactionManager(factory));
        var trades=new group.flyfish.dev.xianyu.trade.TradeService(store,top,bridge,null,new XianyuProperties(),transaction,null);
        store.saveOrder(new Order("refund-order","s","local","3318740388015865620","buyer","XIANYU_MINIAPP",100,"CREATED",2,"NONE",0,"WAITING","WAITING",Map.of(),0,store.now()+900),true).block();
        for(String id:List.of("refund-first-000001","refund-second-000002")) {
            trades.requestRefund("refund-order",50,id,group.flyfish.dev.xianyu.top.TopOperation.PART_REFUND,"1","AGREE_REFUND","Verified support ticket with buyer approval").block();
            Job job=store.claim(store.ready().blockFirst()).block();trades.execute(job).block();store.finish(job,"SUCCESS",null).block();
            long amount=store.order("refund-order").block().refundCents();
            trades.requestRefund("refund-order",50,id,group.flyfish.dev.xianyu.top.TopOperation.PART_REFUND,"1","AGREE_REFUND","Verified support ticket with buyer approval").block();
            assertEquals(amount,store.order("refund-order").block().refundCents());
        }
        assertEquals(100,store.order("refund-order").block().refundCents());assertEquals("REFUNDED",store.order("refund-order").block().refundState());
        org.mockito.Mockito.verify(top,org.mockito.Mockito.times(2)).call(org.mockito.ArgumentMatchers.eq("s"),org.mockito.ArgumentMatchers.eq(group.flyfish.dev.xianyu.top.TopOperation.PART_REFUND),org.mockito.ArgumentMatchers.anyMap());
    }
}
