package group.flyfish.dev.generator.handlers;

import group.flyfish.dev.bean.DbTable;
import group.flyfish.dev.generator.properties.GeneratorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMysqlHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesCodeFromR2dbcMetadataModel() {
        GeneratorProperties properties = new GeneratorProperties();
        properties.setOutputDir(tempDir.toString());
        properties.setIncludes(List.of("t_demo_order"));
        properties.setTablePrefix(List.of("t_"));
        properties.setTables(List.of(table()));

        new DefaultMysqlHandler(properties).generate();

        assertTrue(Files.exists(tempDir.resolve("com/chinaunicom/system/domain/po/DemoOrder.java")));
        assertTrue(Files.exists(tempDir.resolve("com/chinaunicom/system/mapper/DemoOrderMapper.java")));
        assertTrue(Files.exists(tempDir.resolve("com/chinaunicom/system/other/demo-order/index.vue")));
    }

    private DbTable table() {
        DbTable table = new DbTable();
        table.setName("t_demo_order");
        table.setComment("演示订单");

        DbTable.DbColumn id = column("id", "bigint", "主键");
        id.setPrimary(true);
        id.setAutoIncrement(true);

        DbTable.DbColumn name = column("order_name", "varchar", "订单名称");
        name.setLength(64);
        name.setNullable(false);

        table.setColumns(List.of(id, name));
        table.setIndexes(List.of());
        return table;
    }

    private DbTable.DbColumn column(String name, String type, String comment) {
        DbTable.DbColumn column = new DbTable.DbColumn();
        column.setName(name);
        column.setType(type);
        column.setComment(comment);
        column.setNullable(true);
        return column;
    }
}
