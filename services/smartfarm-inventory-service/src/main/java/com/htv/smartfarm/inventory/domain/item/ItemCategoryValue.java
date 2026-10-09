package com.htv.smartfarm.inventory.domain.item;

public enum ItemCategoryValue {
    ITEM_CATEGORY_UNSPECIFIED, ITEM_CATEGORY_FEED, ITEM_CATEGORY_MEDICINE,
    ITEM_CATEGORY_EQUIPMENT, ITEM_CATEGORY_OTHER;

    public static ItemCategoryValue parse(String value) {
        if (value == null || value.isBlank()) return ITEM_CATEGORY_UNSPECIFIED;
        return ItemCategoryValue.valueOf(value.trim().toUpperCase());
    }
}
