package com.ysh.planning.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolCatalogTest {
    @Test
    void exposesLedgerFunctionsWithoutBuiltInWebSearch() {
        String definitions = AgentToolCatalog.definitions().toString();
        assertTrue(definitions.contains("read_budget_summary"));
        assertTrue(definitions.contains("prepare_create_expense"));
        assertTrue(definitions.contains("categoryId"));
        assertTrue(definitions.contains("sortOrder"));
        assertTrue(definitions.contains("additionalProperties=false"));
        assertFalse(definitions.contains("web_search"));
    }
}
