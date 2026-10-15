package com.htv.smartfarm.identity.bootstrap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_identity_bootstrap_lock")
public class IdentityBootstrapLockEntity {

    public static final String SUPERADMIN_LOCK_ID = "superadmin";

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    protected IdentityBootstrapLockEntity() {
    }

    public IdentityBootstrapLockEntity(String id) {
        this.id = id;
    }
}
