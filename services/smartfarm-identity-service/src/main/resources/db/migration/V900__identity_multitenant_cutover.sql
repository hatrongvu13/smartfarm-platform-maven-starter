-- Final cutover support for the new multi-tenant Identity runtime.
-- Rename the version number to the next unused Flyway version before deployment.
-- Legacy refresh rows cannot be safely associated with a tenant, so revoke them.
DELETE FROM sf_refresh;

ALTER TABLE sf_refresh
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);
ALTER TABLE sf_refresh
    ADD COLUMN IF NOT EXISTS audience VARCHAR(100);

UPDATE sf_refresh SET tenant_id = '' WHERE tenant_id IS NULL;
UPDATE sf_refresh SET audience = '' WHERE audience IS NULL;

ALTER TABLE sf_refresh ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE sf_refresh ALTER COLUMN audience SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_refresh_user_tenant
    ON sf_refresh (user_id, tenant_id);

-- The new JPA model owns these tables. They are intentionally not dropped here:
-- sf_user, sf_user_role, legacy sf_role layout and legacy sf_role_permission layout.
-- Perform data migration and destructive cleanup in a separately reviewed migration.
