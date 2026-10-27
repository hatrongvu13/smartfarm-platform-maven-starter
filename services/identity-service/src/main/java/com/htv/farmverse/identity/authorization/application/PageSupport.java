package com.htv.farmverse.identity.authorization.application;

import java.util.List;

import com.htv.farmverse.common.paging.PageResult;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

public final class PageSupport {

    private PageSupport() {
    }

    public static <T> PageResult<T> page(
            List<T> source,
            int requestedSize,
            String pageToken
    ) {
        int size = size(requestedSize);
        int offset = offset(pageToken);
        if (offset >= source.size()) return PageResult.empty();
        int end = Math.min(offset + size, source.size());
        String next = end < source.size() ? Integer.toString(end) : "";
        return new PageResult<>(source.subList(offset, end), next);
    }

    public static Pageable pageable(int requestedSize, String pageToken) {
        int size = size(requestedSize);
        return new OffsetPageRequest(offset(pageToken), size);
    }

    public static <T> PageResult<T> page(Slice<T> source, int offset) {
        if (source.getContent().isEmpty()) return PageResult.empty();
        String next = source.hasNext()
                ? Integer.toString(Math.addExact(offset, source.getNumberOfElements()))
                : "";
        return new PageResult<>(source.getContent(), next);
    }

    private static int size(int requestedSize) {
        return Math.max(1, Math.min(requestedSize <= 0 ? 20 : requestedSize, 100));
    }

    public static int offset(String token) {
        if (token == null || token.isBlank()) return 0;
        try {
            int value = Integer.parseInt(token.trim());
            if (value < 0) throw new IllegalArgumentException("pageToken must not be negative");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pageToken is invalid", exception);
        }
    }

    private record OffsetPageRequest(int offset, int pageSize) implements Pageable {
        private OffsetPageRequest {
            if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
            if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        }

        @Override
        public int getPageNumber() {
            return offset / pageSize;
        }

        @Override
        public int getPageSize() {
            return pageSize;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Sort getSort() {
            return Sort.unsorted();
        }

        @Override
        public Pageable next() {
            return new OffsetPageRequest(Math.addExact(offset, pageSize), pageSize);
        }

        @Override
        public Pageable previousOrFirst() {
            return hasPrevious()
                    ? new OffsetPageRequest(Math.max(0, offset - pageSize), pageSize)
                    : first();
        }

        @Override
        public Pageable first() {
            return new OffsetPageRequest(0, pageSize);
        }

        @Override
        public Pageable withPage(int pageNumber) {
            if (pageNumber < 0) throw new IllegalArgumentException("pageNumber must not be negative");
            return new OffsetPageRequest(Math.multiplyExact(pageNumber, pageSize), pageSize);
        }

        @Override
        public boolean hasPrevious() {
            return offset > 0;
        }
    }
}
