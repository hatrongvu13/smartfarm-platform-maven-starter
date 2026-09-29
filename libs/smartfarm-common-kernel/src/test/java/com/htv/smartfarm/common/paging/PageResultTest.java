package com.htv.smartfarm.common.paging;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;

class PageResultTest {
    @Test
    void copiesItemsAndNormalizesToken() {
        var source = new ArrayList<>(List.of("one"));
        var result = new PageResult<>(source, null);
        source.add("two");
        assertEquals(List.of("one"), result.items());
        assertFalse(result.hasNextPage());
        assertThrows(UnsupportedOperationException.class, () -> result.items().add("three"));
    }
}
