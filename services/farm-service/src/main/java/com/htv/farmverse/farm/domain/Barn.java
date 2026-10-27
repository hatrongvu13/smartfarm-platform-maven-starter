package com.htv.farmverse.farm.domain;

/**
 * Chuồng / khu nuôi trong một trang trại. Value object: chỉ có nghĩa bên trong {@link Farm},
 * được nhận diện bằng {@code code} duy nhất trong farm.
 */
public record Barn(String code, String name, int capacity) {

    public Barn {
        if (code == null || !code.matches("[A-Z0-9][A-Z0-9-]{0,39}")) {
            throw new FarmRuleViolation("SF-FARM-003", "Mã chuồng phải viết hoa, 1-40 ký tự (A-Z, 0-9, -)");
        }
        if (name == null || name.isBlank()) {
            throw new FarmRuleViolation("SF-FARM-004", "Chuồng cần tên");
        }
        if (capacity <= 0) {
            throw new FarmRuleViolation("SF-FARM-005", "Sức chứa phải lớn hơn 0");
        }
    }
}
