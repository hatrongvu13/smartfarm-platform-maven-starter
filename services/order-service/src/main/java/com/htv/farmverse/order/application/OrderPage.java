package com.htv.farmverse.order.application;

import java.util.List;

public record OrderPage(
        List<OrderDraftData> orders,
        String nextPageToken
) {
    public OrderPage {
        orders = List.copyOf(orders);
        nextPageToken = nextPageToken == null ? "" : nextPageToken;
    }
}
