package com.htv.farmverse.farm.adapter.out.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.htv.farmverse.farm.domain.FarmStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Bản lưu của aggregate {@code Farm}. {@link TenantId}: Hibernate tự điền {@code tenant_id} khi lưu và
 * tự thêm {@code where tenant_id = ?} vào mọi truy vấn — code nghiệp vụ không bao giờ truyền tenant.
 */
@Getter
@Setter(AccessLevel.PACKAGE)
@Entity
@Table(name = "farm")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class FarmEntity {

    @Id
    @Column(length = 36)
    private String id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FarmStatus status;

    @ElementCollection
    @CollectionTable(name = "barn", joinColumns = @JoinColumn(name = "farm_id"))
    @OrderColumn(name = "position")
    private List<BarnEmbeddable> barns = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    FarmEntity(String id) {
        this.id = id;
    }
}
