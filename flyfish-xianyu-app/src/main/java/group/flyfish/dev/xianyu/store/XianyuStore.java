package group.flyfish.dev.xianyu.store;

import group.flyfish.dev.xianyu.store.XianyuModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class XianyuStore {
    private final DatabaseClient db;
    private final JsonMapper json;
    public String write(Object value) { return json.writeValueAsString(value); }
    public Map<String, Object> read(String value) {
        return value == null ? Map.of() : json.readValue(value, new TypeReference<>() { });
    }
    public long now() { return Instant.now().getEpochSecond(); }
    private String nullable(String text) { return text == null ? "" : text; }
    private String nullIfEmpty(String text) { return text == null || text.isEmpty() ? null : text; }

    public Flux<Seller> sellers() {
        return db.sql("SELECT * FROM xy_seller ORDER BY seller_key").map((r,m) ->
                new Seller(r.get("seller_key", String.class), r.get("application", String.class),
                        r.get("token_cipher", String.class), r.get("expires_at", Long.class), r.get("enabled", Boolean.class))).all();
    }
    public Mono<Seller> seller(String key) { return sellers().filter(s -> s.key().equals(key)).next(); }
    public Mono<Void> saveSeller(Seller s) {
        return seller(s.key()).flatMap(old -> db.sql("UPDATE xy_seller SET application=:app, token_cipher=:token, expires_at=:expires, enabled=:enabled WHERE seller_key=:key")
                .bind("key", s.key()).bind("app", s.application()).bind("token", s.tokenCipher())
                .bind("expires", s.expiresAt()).bind("enabled", s.enabled()).fetch().rowsUpdated())
                .switchIfEmpty(Mono.defer(() -> db.sql("INSERT INTO xy_seller VALUES (:key,:app,:token,:expires,:enabled)")
                        .bind("key", s.key()).bind("app", s.application()).bind("token", s.tokenCipher())
                        .bind("expires", s.expiresAt()).bind("enabled", s.enabled()).fetch().rowsUpdated())).then();
    }
    public Flux<Capability> capabilities() {
        return db.sql("SELECT * FROM xy_capability ORDER BY capability_key").map((r,m) ->
                new Capability(r.get("seller_key", String.class), r.get("operation", String.class),
                        r.get("granted", Boolean.class), r.get("verified", Boolean.class), r.get("expires_at", Long.class),
                        nullIfEmpty(r.get("request_key", String.class)),
                        json.readValue(r.get("topics", String.class), new TypeReference<List<String>>() { }),
                        json.readValue(r.get("category_ids", String.class), new TypeReference<List<String>>() { }),r.get("application",String.class))).all();
    }
    public Mono<Capability> capability(String seller, String operation,String application) {
        return capabilities().filter(c -> c.seller().equals(seller) && c.operation().equals(operation) && c.application().equals(application)).next();
    }
    public Mono<Void> saveCapability(Capability c) {
        String key = c.application()+":"+c.seller() + ":" + c.operation();
        return db.sql("INSERT INTO xy_capability (capability_key,seller_key,operation,granted,verified,expires_at,request_key,topics,category_ids,updated_at,application) VALUES (:key,:seller,:op,:granted,:verified,:expires,:request,:topics,:categories,:now,:app) ON DUPLICATE KEY UPDATE capability_key=VALUES(capability_key),granted=VALUES(granted),verified=VALUES(verified),expires_at=VALUES(expires_at),request_key=VALUES(request_key),topics=VALUES(topics),category_ids=VALUES(category_ids),updated_at=VALUES(updated_at)")
                        .bind("key", key).bind("seller", c.seller()).bind("op", c.operation())
                        .bind("granted", c.granted()).bind("verified", c.verified()).bind("expires", c.expiresAt())
                        .bind("request", nullable(c.requestKey())).bind("topics", write(c.topics() == null ? List.of() : c.topics()))
                        .bind("categories", write(c.categoryIds() == null ? List.of() : c.categoryIds()))
                        .bind("now", now()).bind("app",c.application()).fetch().rowsUpdated().then();
    }
    public Flux<Listing> listings() {
        return db.sql("SELECT * FROM xy_listing ORDER BY updated_at DESC LIMIT 200").map((r,m) ->
                new Listing(r.get("listing_key", String.class), r.get("seller_key", String.class),
                        r.get("local_item_id", String.class), r.get("remote_item_id", String.class), r.get("revision", Long.class),
                        r.get("status", String.class), r.get("native_enabled", Boolean.class),
                        read(r.get("payload", String.class)), read(r.get("remote_snapshot", String.class)))).all();
    }
    public Mono<Listing> listing(String key) {
        return db.sql("SELECT * FROM xy_listing WHERE listing_key=:key").bind("key", key).map((r,m) ->
                new Listing(key, r.get("seller_key", String.class), r.get("local_item_id", String.class),
                        r.get("remote_item_id", String.class), r.get("revision", Long.class), r.get("status", String.class),
                        r.get("native_enabled", Boolean.class), read(r.get("payload", String.class)), read(r.get("remote_snapshot", String.class)))).one();
    }
    public Flux<Listing> sellerListings(String seller) {
        return db.sql("SELECT listing_key FROM xy_listing WHERE seller_key=:seller ORDER BY listing_key")
                .bind("seller",seller).map((r,m)->r.get("listing_key",String.class)).all().concatMap(this::listing);
    }
    public Mono<Listing> nativeListing(String seller,String remote) {
        return sellerListings(seller).filter(l->l.nativeEnabled() && remote.equals(l.remoteItemId())
                && java.util.Set.of("SYNCED","DIFFERENT").contains(l.status())).singleOrEmpty();
    }
    @org.springframework.transaction.annotation.Transactional
    public Mono<Boolean> saveListing(Listing l, boolean insert) {
        var query = db.sql(insert
                ? "INSERT INTO xy_listing VALUES (:key,:seller,:local,:remote,:next,:status,:native,:payload,:snapshot,:now)"
                : "UPDATE xy_listing SET remote_item_id=:remote, revision=:next, status=:status, native_enabled=:native, payload=:payload, remote_snapshot=:snapshot, updated_at=:now WHERE listing_key=:key AND revision=:revision");
        query = query.bind("key", l.key()).bind("remote", nullable(l.remoteItemId())).bind("next", l.revision() + 1)
                .bind("status", l.status()).bind("native", l.nativeEnabled()).bind("payload", write(l.payload()))
                .bind("snapshot", write(l.remoteSnapshot())).bind("now", now());
        query = insert ? query.bind("seller", l.seller()).bind("local", l.localItemId()) : query.bind("revision", l.revision());
        return query.fetch().rowsUpdated().flatMap(n -> n!=1 ? Mono.just(false) : db.sql("INSERT INTO xy_listing_revision VALUES (:key,:revision,:status,:payload,:snapshot,:now)")
                .bind("key",l.key()).bind("revision",l.revision()+1).bind("status",l.status()).bind("payload",write(l.payload()))
                .bind("snapshot",write(l.remoteSnapshot())).bind("now",now()).fetch().rowsUpdated().thenReturn(true));
    }
    public Flux<Order> orders() {
        return db.sql("SELECT * FROM xy_order ORDER BY updated_at DESC LIMIT 200").map((r,m) -> orderRow(r)).all();
    }
    public Mono<Order> order(String key) {
        return db.sql("SELECT * FROM xy_order WHERE order_key=:key").bind("key", key).map((r,m) -> orderRow(r)).one();
    }
    private Order orderRow(io.r2dbc.spi.Row r) {
        return new Order(r.get("order_key", String.class), r.get("seller_key", String.class),
                r.get("local_order_no", String.class), r.get("remote_order_id", String.class), r.get("buyer_subject", String.class),
                r.get("channel", String.class), r.get("amount_cents", Long.class), r.get("creation_state", String.class),
                r.get("platform_state", Integer.class), r.get("refund_state", String.class), r.get("refund_cents", Long.class),
                r.get("fulfillment_state", String.class), r.get("shipment_state", String.class),
                read(r.get("snapshot", String.class)), r.get("revision", Long.class), r.get("expires_at", Long.class));
    }
    public Mono<Boolean> saveOrder(Order o, boolean insert) {
        var query = db.sql(insert
                ? "INSERT INTO xy_order VALUES (:key,:seller,:local,:remote,:buyer,:channel,:amount,:creation,:platform,:refund,:refunded,:fulfillment,:shipment,:snapshot,:next,:expires,:now)"
                : "UPDATE xy_order SET local_order_no=:local, remote_order_id=:remote, buyer_subject=:buyer, amount_cents=:amount, creation_state=:creation, platform_state=:platform, refund_state=:refund, refund_cents=:refunded, fulfillment_state=:fulfillment, shipment_state=:shipment, snapshot=:snapshot, revision=:next, expires_at=:expires, updated_at=:now WHERE order_key=:key AND revision=:revision");
        query = query.bind("key", o.key()).bind("amount", o.amountCents()).bind("creation", o.creationState())
                .bind("platform", o.platformState()).bind("refund", o.refundState()).bind("refunded", o.refundCents())
                .bind("fulfillment", o.fulfillmentState()).bind("shipment", o.shipmentState()).bind("snapshot", write(o.snapshot()))
                .bind("next", o.revision() + 1).bind("expires", o.expiresAt()).bind("now", now());
        query = o.localOrderNo() == null ? query.bindNull("local", String.class) : query.bind("local", o.localOrderNo());
        query = o.remoteOrderId() == null ? query.bindNull("remote", String.class) : query.bind("remote", o.remoteOrderId());
        query = o.buyerSubject() == null ? query.bindNull("buyer", String.class) : query.bind("buyer", o.buyerSubject());
        query = insert ? query.bind("seller", o.seller()).bind("channel", o.channel()) : query.bind("revision", o.revision());
        return query.fetch().rowsUpdated().map(n -> n == 1);
    }
    public Mono<Void> enqueue(String key, String seller, String kind, Map<String, Object> payload) {
        return db.sql("INSERT INTO xy_job (job_key,seller_key,kind,payload,status,attempts,next_at,lease_until,created_at,updated_at) VALUES (:key,:seller,:kind,:payload,'READY',0,:now,0,:now,:now)")
                .bind("key", key).bind("seller", seller).bind("kind", kind).bind("payload", write(payload)).bind("now", now())
                .fetch().rowsUpdated().onErrorResume(DuplicateKeyException.class, e -> Mono.just(0L)).then();
    }
    public Flux<Job> jobs() { return db.sql("SELECT * FROM xy_job ORDER BY updated_at DESC LIMIT 200").map((r,m) -> jobRow(r)).all(); }
    public Mono<Job> job(String key) {
        return db.sql("SELECT * FROM xy_job WHERE job_key=:key").bind("key",key).map((r,m)->jobRow(r)).one();
    }
    private Job jobRow(io.r2dbc.spi.Row r) {
        return new Job(r.get("job_key", String.class), r.get("seller_key", String.class), r.get("kind", String.class),
                read(r.get("payload", String.class)), r.get("status", String.class), r.get("attempts", Integer.class),
                r.get("next_at", Long.class), r.get("lease_until", Long.class), r.get("lease_token", String.class), r.get("last_error", String.class));
    }
    public Flux<Job> ready() {
        return db.sql("SELECT * FROM xy_job WHERE status='READY' AND next_at<=:now ORDER BY next_at LIMIT 20")
                .bind("now", now()).map((r,m) -> jobRow(r)).all();
    }
    public Mono<Job> claim(Job j) {
        String lease = UUID.randomUUID().toString();
        return db.sql("UPDATE xy_job SET status='RUNNING',lease_token=:lease,lease_until=:until,attempts=attempts+1,updated_at=:now WHERE job_key=:key AND status='READY'")
                .bind("key", j.key()).bind("lease", lease).bind("until", now() + 300).bind("now", now())
                .fetch().rowsUpdated().filter(n -> n == 1).map(n ->
                        new Job(j.key(), j.seller(), j.kind(), j.payload(), "RUNNING", j.attempts()+1, j.nextAt(), now()+300, lease, null));
    }
    public Mono<Void> finish(Job j, String status, String error) {
        return db.sql("UPDATE xy_job SET status=:status,last_error=:error,lease_until=0,next_at=:next,updated_at=:now WHERE job_key=:key AND lease_token=:lease AND status='RUNNING'")
                .bind("key", j.key()).bind("lease", j.leaseToken()).bind("status", status).bind("error", nullable(error))
                .bind("next", now() + Math.min(3600, 5L << Math.min(j.attempts(), 9))).bind("now", now()).fetch().rowsUpdated().then();
    }
    public Mono<Void> recoverLeases() {
        // Remote mutations may have succeeded before a crash; they require reconciliation, never blind replay.
        return db.sql("UPDATE xy_job SET status=CASE WHEN kind IN ('ORDER_CREATE','ITEM_PUBLISH','REFUND') THEN 'UNKNOWN' ELSE 'READY' END,lease_until=0,updated_at=:now WHERE status='RUNNING' AND lease_until<:now")
                .bind("now", now()).fetch().rowsUpdated().thenMany(db.sql("SELECT * FROM xy_job WHERE status='UNKNOWN' AND kind IN ('ORDER_CREATE','ITEM_PUBLISH')")
                        .map((r,m)->jobRow(r)).all()).concatMap(j -> {
                    if(j.kind().equals("ORDER_CREATE")) return db.sql("UPDATE xy_order SET creation_state='UNKNOWN',revision=revision+1 WHERE order_key=:key AND remote_order_id IS NULL AND creation_state<>'UNKNOWN'")
                            .bind("key",String.valueOf(j.payload().get("orderKey"))).fetch().rowsUpdated();
                    return db.sql("UPDATE xy_listing SET status='UNKNOWN',revision=revision+1 WHERE listing_key=:key AND remote_item_id='' AND status<>'UNKNOWN'")
                            .bind("key",String.valueOf(j.payload().get("listingKey"))).fetch().rowsUpdated();
                }).then();
    }
    public Mono<Boolean> receive(String id, String seller, String topic, String payload) {
        return db.sql("INSERT INTO xy_message (message_key,seller_key,topic,payload,created_at) VALUES (:id,:seller,:topic,:payload,:now)")
                .bind("id", seller + ":" + id).bind("seller", seller).bind("topic", topic).bind("payload", payload).bind("now", now())
                .fetch().rowsUpdated().map(n -> n == 1).onErrorResume(DuplicateKeyException.class, e -> Mono.just(false));
    }
    public Mono<Void> messageState(String id,String seller,String status,String error) {
        return db.sql("UPDATE xy_message SET status=:status,last_error=:error WHERE message_key=:id")
                .bind("id",seller+":"+id).bind("status",status).bind("error",error==null ? "" : error).fetch().rowsUpdated().then();
    }
    public Mono<Void> audit(String actor, String operation, String key) {
        return db.sql("INSERT INTO xy_audit VALUES (:id,:actor,:op,:key,:now)")
                .bind("id", UUID.randomUUID().toString()).bind("actor", actor).bind("op", operation).bind("key", key).bind("now", now())
                .fetch().rowsUpdated().then();
    }
    public DatabaseClient database() { return db; }
}
