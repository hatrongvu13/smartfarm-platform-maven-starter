package com.htv.smartfarm.common.paging;

public record PageQuery(int pageSize, String pageToken) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAXIMUM_PAGE_SIZE = 100;

    public PageQuery {
        if (pageSize < 1 || pageSize > MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAXIMUM_PAGE_SIZE);
        }
        pageToken = pageToken == null ? "" : pageToken.trim();
    }

    public static PageQuery firstPage() {
        return new PageQuery(DEFAULT_PAGE_SIZE, "");
    }

    public static PageQuery of(Integer requestedPageSize, String pageToken) {
        int resolved = requestedPageSize == null || requestedPageSize < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(requestedPageSize, MAXIMUM_PAGE_SIZE);
        return new PageQuery(resolved, pageToken);
    }

    public boolean first() {
        return pageToken.isEmpty();
    }
}
