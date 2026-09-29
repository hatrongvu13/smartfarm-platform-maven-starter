package com.htv.smartfarm.common.paging;

import java.util.List;

public record PageResult<T>(List<T> items, String nextPageToken) {
    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
        nextPageToken = nextPageToken == null ? "" : nextPageToken.trim();
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>(List.of(), "");
    }

    public static <T> PageResult<T> lastPage(List<T> items) {
        return new PageResult<>(items, "");
    }

    public boolean hasNextPage() {
        return !nextPageToken.isEmpty();
    }
}
