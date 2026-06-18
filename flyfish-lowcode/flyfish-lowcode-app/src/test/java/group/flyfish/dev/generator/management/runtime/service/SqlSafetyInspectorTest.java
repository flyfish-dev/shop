package group.flyfish.dev.generator.management.runtime.service;

import group.flyfish.dev.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSafetyInspectorTest {

    private final SqlSafetyInspector inspector = new SqlSafetyInspector();

    @Test
    void normalizesSingleStatement() {
        assertEquals("select 1", inspector.normalizeSingleStatement(" select 1; "));
    }

    @Test
    void rejectsMultipleStatements() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> inspector.normalizeSingleStatement("select 1; select 2"));

        assertEquals("SQL_MULTI_STATEMENT_DENIED", exception.getCode());
    }

    @Test
    void requiresReadOnlySqlByDefault() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> inspector.requireReadOnly("delete from user"));

        assertEquals("SQL_READ_ONLY_REQUIRED", exception.getCode());
    }

    @Test
    void acceptsReadonlySql() {
        assertTrue(inspector.isReadOnly("show tables"));
    }
}
