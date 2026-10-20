package group.flyfish.dev.shop.repository;

import group.flyfish.dev.shop.domain.po.ShopItem;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.convert.MappingR2dbcConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.DefaultNamingStrategy;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShopItemR2dbcMappingTest {

    @Test
    void shouldReadAllItemFieldsWithStandardPropertyMapping() {
        ConnectionFactory connectionFactory = ConnectionFactories.get(
                "r2dbc:h2:mem:///shop-item-mapping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        R2dbcCustomConversions conversions = R2dbcCustomConversions.of(dialect);
        R2dbcMappingContext mappingContext = R2dbcMappingContext.forPlainIdentifiers(
                DefaultNamingStrategy.INSTANCE);
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setManagedTypes(RelationalManagedTypes.from(ShopItem.class));
        MappingR2dbcConverter converter = new MappingR2dbcConverter(mappingContext, conversions);
        R2dbcEntityTemplate template = new R2dbcEntityTemplate(
                DatabaseClient.create(connectionFactory), dialect, converter);

        Mono<Void> setup = template.getDatabaseClient().sql("""
                        CREATE TABLE shop_item (
                            id BIGINT PRIMARY KEY,
                            name VARCHAR(64), cover VARCHAR(256), images VARCHAR(1024),
                            price DECIMAL(10, 2), usd_price DECIMAL(10, 2),
                            group_id BIGINT, shop_id BIGINT, type VARCHAR(32), delivery_mode VARCHAR(16),
                            tags VARCHAR(256), params VARCHAR(1024), buy_count INT, description VARCHAR(1024),
                            i18n VARCHAR(1024), sort INT, enabled BOOLEAN, pinned BOOLEAN, recommended BOOLEAN,
                            highlight_style VARCHAR(32), highlight_icon VARCHAR(32),
                            default_coupon_enabled BOOLEAN, default_coupon_code VARCHAR(64), sku_mode VARCHAR(12),
                            create_by VARCHAR(32), create_time TIMESTAMP, update_by VARCHAR(32),
                            update_time TIMESTAMP, is_delete BOOLEAN
                        )
                        """).then()
                .then(template.getDatabaseClient().sql("""
                        INSERT INTO shop_item (
                            id, name, price, group_id, shop_id, type, delivery_mode, tags, buy_count,
                            description, sort, enabled, pinned, recommended, default_coupon_enabled,
                            sku_mode, create_by, create_time, update_by, update_time, is_delete
                        ) VALUES (
                            1001, '专享链接回归商品', 199.00, 1, 1, 'SERVICE_PACKAGE', 'NONE',
                            '专享,测试', 0, '用于验证专享优惠链接', 1, TRUE, FALSE, FALSE, FALSE,
                            'SINGLE', '1', CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, FALSE
                        )
                        """).then());
        StepVerifier.create(setup).verifyComplete();

        StepVerifier.create(template.selectOne(Query.query(Criteria.where("id").is(1001L)), ShopItem.class))
                .assertNext(item -> {
                    assertThat(item.getName()).isEqualTo("专享链接回归商品");
                    assertThat(item.getPrice()).isEqualByComparingTo(new BigDecimal("199.00"));
                    assertThat(item.getGroupId()).isEqualTo(1L);
                    assertThat(item.getShopId()).isEqualTo(1L);
                    assertThat(item.getType()).isEqualTo(ShopItem.Type.SERVICE_PACKAGE);
                    assertThat(item.getDeliveryMode()).isEqualTo(ShopItem.DeliveryMode.NONE);
                    assertThat(item.getEnabled()).isTrue();
                    assertThat(item.getSkuMode()).isEqualTo(ShopItem.SkuMode.SINGLE);
                })
                .verifyComplete();
    }
}
