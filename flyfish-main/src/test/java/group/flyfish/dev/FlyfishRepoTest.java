package group.flyfish.dev;

import group.flyfish.dev.bean.DbSource;
import group.flyfish.dev.generator.management.repository.DbSourceRepository;
import group.flyfish.dev.shop.domain.po.Shop;
import group.flyfish.dev.shop.repository.ShopRepository;
import group.flyfish.dev.user.config.context.ReactiveUserContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.util.UUID;

@SpringBootTest
public class FlyfishRepoTest {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private DbSourceRepository dbSourceRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    public void r2dbcAuditingFillsCurrentUserFromReactiveContext() {
        String name = "audit-test-" + UUID.randomUUID();
        Shop shop = new Shop();
        shop.setName(name);
        shop.setDescription(name);
        shop.setAvatar("/audit-test.png");

        StepVerifier.create(shopRepository.save(shop)
                        .contextWrite(context -> context.put(ReactiveUserContextHolder.CONTEXT_KEY, 42L)))
                .expectNextMatches(saved -> "42".equals(saved.getCreateBy()) && "42".equals(saved.getUpdateBy()))
                .verifyComplete();
    }

    @Test
    public void logicalDeleteIsTransparent() {
        String name = "logic-delete-test-" + UUID.randomUUID();
        Shop shop = new Shop();
        shop.setName(name);
        shop.setDescription(name);
        shop.setAvatar("/logic-delete-test.png");
        shop.setCreateBy("test");
        shop.setUpdateBy("test");

        StepVerifier.create(shopRepository.save(shop)
                        .flatMap(saved -> shopRepository.deleteById(saved.getId()).thenReturn(saved.getId()))
                        .flatMap(id -> shopRepository.findById(id)
                                .hasElement()
                                .zipWith(databaseClient.sql("select is_delete from shop where id = :id")
                                        .bind("id", id)
                                        .map((row, metadata) -> row.get("is_delete", Boolean.class))
                                        .one())))
                .expectNextMatches(tuple -> !tuple.getT1() && Boolean.TRUE.equals(tuple.getT2()))
                .verifyComplete();
    }

    @Test
    public void dbSourceKeyRoundTrips() {
        String key = "db-source-test-" + UUID.randomUUID();
        DbSource source = new DbSource()
                .setKey(key)
                .setName("db-source-test")
                .setUrl("r2dbc:mysql://127.0.0.1:3306/flyfish")
                .setType("mysql")
                .setHost("127.0.0.1")
                .setPort(3306)
                .setDatabaseName("flyfish")
                .setUsername("root")
                .setPassword("password")
                .setOwner("public");

        StepVerifier.create(dbSourceRepository.save(source)
                        .flatMap(saved -> dbSourceRepository.findById(saved.getId()))
                        .map(DbSource::getKey))
                .expectNext(key)
                .verifyComplete();

        StepVerifier.create(dbSourceRepository.findByKey(key).map(DbSource::getKey))
                .expectNext(key)
                .verifyComplete();
    }
}
