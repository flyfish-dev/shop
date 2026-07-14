package group.flyfish.dev.ddl.parser;

import group.flyfish.dev.annotations.data.Property;
import group.flyfish.dev.bean.DbTable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelParserTest {

    @Test
    void parsesSpringAndFlyfishMetadataWithoutPrivateFrameworkDependencies() {
        DbTable table = ModelParser.parse(TestModel.class);

        assertThat(table.getName()).isEqualTo("test_models");
        assertThat(table.getColumns()).extracting(DbTable.DbColumn::getName)
                .containsExactly("model_id", "display_name", "description", "tags");

        DbTable.DbColumn id = table.getColumns().get(0);
        assertThat(id.getPrimary()).isTrue();
        assertThat(id.getNullable()).isFalse();
        assertThat(id.getLength()).isEqualTo(36);

        DbTable.DbColumn displayName = table.getColumns().get(1);
        assertThat(displayName.getComment()).isEqualTo("展示名称");
        assertThat(displayName.getNullable()).isFalse();

        DbTable.DbColumn description = table.getColumns().get(2);
        assertThat(description.getLength()).isEqualTo(1000);
        assertThat(description.getDefaultValue()).isEqualTo("暂无");

        DbTable.DbColumn tags = table.getColumns().get(3);
        assertThat(tags.getType()).isEqualTo("JSON");
    }

    @Table("test_models")
    private static class TestModel {

        @Id
        @Column("model_id")
        private String id;

        @NotBlank
        @Property(value = "展示名称", key = "display_name")
        private String name;

        @Size(max = 1000)
        @Property("描述")
        private String description = "暂无";

        private List<String> tags;

        @Transient
        private String runtimeOnly;
    }
}
