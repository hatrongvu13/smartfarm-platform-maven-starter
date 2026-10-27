package com.htv.farmverse.farm.domain;

/** Vi phạm luật nghiệp vụ của aggregate. {@code code} theo định dạng {@code SF-FARM-xxx}. */
public class FarmRuleViolation extends RuntimeException {

    private final String code;

    public FarmRuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
