package group.flyfish.dev.generator.management.utils;

import group.flyfish.dev.bean.DbSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class R2dbcUrlUtilsTest {

    @Test
    void buildsConnectionUrlFromStructuredSource() {
        DbSource source = new DbSource()
                .setHost("127.0.0.1")
                .setPort(3306)
                .setDatabaseName("flyfish")
                .setParams("?sslMode=DISABLED&serverZoneId=Asia/Shanghai");

        assertEquals("r2dbc:mysql://127.0.0.1:3306/flyfish?serverZoneId=Asia/Shanghai",
                R2dbcUrlUtils.connectionUrl(source));
    }

    @Test
    void materializesLegacyUrlIntoStructuredFields() {
        DbSource source = new DbSource()
                .setUrl("jdbc:mysql://mysql.example.com:3307/flyfish?useSSL=false&serverTimezone=Asia/Shanghai");

        R2dbcUrlUtils.materialize(source);

        assertEquals("mysql", source.getType());
        assertEquals("mysql.example.com", source.getHost());
        assertEquals(3307, source.getPort());
        assertEquals("flyfish", source.getDatabaseName());
        assertEquals("serverZoneId=Asia/Shanghai", source.getParams());
        assertEquals("r2dbc:mysql://mysql.example.com:3307/flyfish?serverZoneId=Asia/Shanghai",
                source.getUrl());
    }

    @Test
    void dropsExplicitDisabledSslFromStructuredParams() {
        DbSource source = new DbSource()
                .setHost("127.0.0.1")
                .setPort(3306)
                .setDatabaseName("flyfish")
                .setParams("sslMode=DISABLED&serverZoneId=Asia/Shanghai");

        R2dbcUrlUtils.materialize(source);

        assertEquals("r2dbc:mysql://127.0.0.1:3306/flyfish?serverZoneId=Asia/Shanghai", source.getUrl());
    }
}
