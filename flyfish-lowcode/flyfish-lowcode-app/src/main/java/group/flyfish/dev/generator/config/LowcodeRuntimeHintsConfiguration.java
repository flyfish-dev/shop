package group.flyfish.dev.generator.config;

import group.flyfish.dev.common.config.RuntimeHintsSupport;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(LowcodeRuntimeHintsConfiguration.LowcodeRuntimeHints.class)
public class LowcodeRuntimeHintsConfiguration {

    public static class LowcodeRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            RuntimeHintsSupport.registerConfigurationProperties(hints, classLoader,
                    "group.flyfish.dev.generator.properties.DevProperties",
                    "group.flyfish.dev.generator.properties.GeneratorProperties",
                    "group.flyfish.dev.generator.properties.GeneratorProperties$PackageProperties",
                    "group.flyfish.dev.generator.properties.GeneratorProperties$R2dbcProperties");
            RuntimeHintsSupport.registerReflectiveTypes(hints, classLoader,
                    "group.flyfish.dev.bean.DbRelation",
                    "group.flyfish.dev.bean.DbSource",
                    "group.flyfish.dev.bean.DbTable",
                    "group.flyfish.dev.bean.DbTest",
                    "group.flyfish.dev.bean.DbURL",
                    "group.flyfish.dev.generator.management.data.bean.TableDataChange",
                    "group.flyfish.dev.generator.management.data.bean.TableDataDeleteDto",
                    "group.flyfish.dev.generator.management.data.bean.TableDataQo",
                    "group.flyfish.dev.generator.management.data.bean.TableDataQo$QueryCriteria",
                    "group.flyfish.dev.generator.management.data.bean.TableDataQo$QueryCriteriaLink",
                    "group.flyfish.dev.generator.management.data.bean.TableDataQo$QueryCriteriaOperation",
                    "group.flyfish.dev.generator.management.data.bean.TableDataRow",
                    "group.flyfish.dev.generator.management.data.bean.TableDataUpdateDto",
                    "group.flyfish.dev.generator.management.data.bean.row.TableIdentity",
                    "group.flyfish.dev.generator.management.data.bean.row.TableRowDeleteDto",
                    "group.flyfish.dev.generator.management.data.bean.row.TableRowInsertDto",
                    "group.flyfish.dev.generator.management.data.bean.row.TableRowUpdateDto",
                    "group.flyfish.dev.generator.management.query.bean.SqlQueryQo",
                    "group.flyfish.dev.generator.management.runtime.bean.SqlRunRequest",
                    "group.flyfish.dev.generator.management.runtime.bean.SqlRunResult",
                    "group.flyfish.dev.generator.management.testing.bean.IntegrationAssertionType",
                    "group.flyfish.dev.generator.management.testing.bean.IntegrationTestCaseRequest",
                    "group.flyfish.dev.generator.management.testing.bean.IntegrationTestCaseResult",
                    "group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunRequest",
                    "group.flyfish.dev.generator.management.testing.bean.IntegrationTestRunResult");
        }
    }
}
