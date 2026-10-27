CREATE INDEX ix_authenticator_pending_expiry
    ON sf_user_authenticator(status, enrollment_expires_at);
