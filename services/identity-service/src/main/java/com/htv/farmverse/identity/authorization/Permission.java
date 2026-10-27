package com.htv.farmverse.identity.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một quyền trong danh mục chung, mã dạng {@code <miền>:<tài nguyên>:<hành động>}. */
@Getter
@Entity
@Table(name = "permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Id
    @Column(length = 80)
    private String code;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 255)
    private String description;

    /** Quyền do nền tảng khai (seed từ cấu hình); không cho sửa / xóa qua API. */
    @Column(name = "system_owned", nullable = false)
    private boolean systemOwned;

    public Permission(String code, String resourceType, String action, String description, boolean systemOwned) {
        this.code = code;
        this.resourceType = resourceType;
        this.action = action;
        this.description = description;
        this.systemOwned = systemOwned;
    }

    public void describe(String description) {
        this.description = description;
    }
}
