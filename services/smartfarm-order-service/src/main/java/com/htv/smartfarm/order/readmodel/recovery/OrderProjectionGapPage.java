package com.htv.smartfarm.order.readmodel.recovery;

import java.util.List;

public record OrderProjectionGapPage(
        List<OrderProjectionGapDetails> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) { }
