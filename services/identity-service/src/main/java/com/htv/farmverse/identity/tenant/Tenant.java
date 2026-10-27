package com.htv.farmverse.identity.tenant;

import com.htv.farmverse.identity.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một tổ chức dùng FarmVerse (ví dụ một công ty trang trại). Dữ liệu nghiệp vụ tách theo tenant. */
@Getter
@Entity
@Table(name = "tenant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseEntity {

    /** Tenant của đội vận hành nền tảng; super admin thuộc tenant này. */
    public static final String PLATFORM_CODE = "platform";

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    public Tenant(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
