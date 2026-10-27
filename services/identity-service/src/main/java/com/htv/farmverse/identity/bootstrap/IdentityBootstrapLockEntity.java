package com.htv.farmverse.identity.bootstrap;

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

    @Column(name = "bootstrap_token_hash", length = 64)
    private String bootstrapTokenHash;

    protected IdentityBootstrapLockEntity() {
    }

    public IdentityBootstrapLockEntity(String id) {
        this.id = id;
    }

    public String getBootstrapTokenHash() {
        return bootstrapTokenHash;
    }

    public void setBootstrapTokenHash(String bootstrapTokenHash) {
        this.bootstrapTokenHash = bootstrapTokenHash;
    }
}
