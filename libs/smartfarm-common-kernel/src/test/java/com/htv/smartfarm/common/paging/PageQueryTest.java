package com.htv.smartfarm.common.paging;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PageQueryTest {
    @Test
    void createsDefaultPage() {
        var q = PageQuery.firstPage();
        assertEquals(20, q.pageSize());
        assertTrue(q.first());
    }

    @Test
    void capsExternalPageSize() {
        assertEquals(100, PageQuery.of(500, null).pageSize());
    }

    @Test
    void rejectsInvalidConstructorValue() {
        assertThrows(IllegalArgumentException.class, () -> new PageQuery(0, null));
    }
}
