package com.htv.smartfarm.order.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class OrderDraftValidation {

    record ValidatedLine(
            String itemId,
            String warehouseId,
            String quantity,
            String quantityUnit,
            String currencyCode,
            long unitPriceMinor,
            long lineTotalMinor
    ) { }

    record ValidatedDraft(String currencyCode, long totalMinor, List<ValidatedLine> lines) { }

    private OrderDraftValidation() {
    }

    static ValidatedDraft validate(List<OrderDraftLineCommand> requestedLines) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new IllegalArgumentException("at least one order line is required");
        }
        if (requestedLines.size() > 200) {
            throw new IllegalArgumentException("an order must not contain more than 200 lines");
        }
        String currency = null;
        long total = 0;
        List<ValidatedLine> values = new ArrayList<>();
        for (int index = 0; index < requestedLines.size(); index++) {
            OrderDraftLineCommand line = requestedLines.get(index);
            if (line == null) throw new IllegalArgumentException("line " + index + " is required");
            String itemId = required(line.itemId(), "line " + index + " itemId");
            String warehouseId = required(line.warehouseId(), "line " + index + " warehouseId");
            String quantityUnit = required(line.quantityUnit(), "line " + index + " quantityUnit");
            String currencyCode = required(line.currencyCode(), "line " + index + " currencyCode").toUpperCase();
            if (currencyCode.length() != 3) {
                throw new IllegalArgumentException("line " + index + " currencyCode must contain 3 characters");
            }
            if (currency == null) currency = currencyCode;
            else if (!currency.equals(currencyCode)) {
                throw new IllegalArgumentException("all order lines must use the same currency");
            }
            BigDecimal quantity;
            try {
                quantity = new BigDecimal(required(line.quantity(), "line " + index + " quantity"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("line " + index + " quantity must be a decimal number");
            }
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("line " + index + " quantity must be positive");
            }
            if (line.unitPriceMinor() <= 0) {
                throw new IllegalArgumentException("line " + index + " unitPriceMinor must be positive");
            }
            long lineTotal = quantity.multiply(BigDecimal.valueOf(line.unitPriceMinor()))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            total = Math.addExact(total, lineTotal);
            values.add(new ValidatedLine(
                    itemId, warehouseId, quantity.toPlainString(), quantityUnit,
                    currencyCode, line.unitPriceMinor(), lineTotal));
        }
        return new ValidatedDraft(currency, total, List.copyOf(values));
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
