package com.htv.farmverse.farm.adapter.out.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BarnEmbeddable {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int capacity;

    BarnEmbeddable(String code, String name, int capacity) {
        this.code = code;
        this.name = name;
        this.capacity = capacity;
    }
}
