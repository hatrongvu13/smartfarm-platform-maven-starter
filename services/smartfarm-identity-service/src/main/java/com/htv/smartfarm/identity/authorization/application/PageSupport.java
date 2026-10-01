package com.htv.smartfarm.identity.authorization.application;

import java.util.List;

import com.htv.smartfarm.common.paging.PageResult;

public final class PageSupport {

    private PageSupport() {
    }

    public static <T> PageResult<T> page(
            List<T> source,
            int requestedSize,
            String pageToken
    ) {
        int size = Math.max(1, Math.min(requestedSize <= 0 ? 20 : requestedSize, 100));
        int offset = parseOffset(pageToken);
        if (offset >= source.size()) return PageResult.empty();
        int end = Math.min(offset + size, source.size());
        String next = end < source.size() ? Integer.toString(end) : "";
        return new PageResult<>(source.subList(offset, end), next);
    }

    private static int parseOffset(String token) {
        if (token == null || token.isBlank()) return 0;
        try {
            int value = Integer.parseInt(token.trim());
            if (value < 0) throw new IllegalArgumentException("pageToken must not be negative");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pageToken is invalid", exception);
        }
    }
}
