package com.htv.farmverse.identity.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.SliceImpl;

class PageSupportTest {

    @Test
    void preservesOffsetTokensWhenPageSizeChanges() {
        var pageable = PageSupport.pageable(3, "5");

        assertThat(pageable.getOffset()).isEqualTo(5);
        assertThat(pageable.getPageSize()).isEqualTo(3);
    }

    @Test
    void returnsTheNextOffsetOnlyWhenMoreResultsExist() {
        var pageable = PageSupport.pageable(2, "4");
        var page = new SliceImpl<>(List.of("a", "b"), pageable, true);

        assertThat(PageSupport.page(page, 4).nextPageToken()).isEqualTo("6");
        assertThat(PageSupport.page(
                new SliceImpl<>(List.of("a"), pageable, false),
                4
        ).nextPageToken()).isEmpty();
    }
}
