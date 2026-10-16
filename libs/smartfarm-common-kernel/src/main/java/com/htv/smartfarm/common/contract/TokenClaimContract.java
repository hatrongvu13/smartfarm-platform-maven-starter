package com.htv.smartfarm.common.contract;

public final class TokenClaimContract {

    public static final String CONTRACT_VERSION_CLAIM = "identity_claims_version";
    public static final int CURRENT_CONTRACT_VERSION = 1;

    public static final String SUBJECT = "sub";
    public static final String TENANT_ID = "tenant_id";
    public static final String AUDIENCE = "aud";
    public static final String TOKEN_TYPE = "token_type";
    public static final String CLIENT_ID = "client_id";
    public static final String AUTHORIZATION_VERSION = "authorization_version";
    public static final String TENANT_AUTHORIZATION_VERSION =
            "tenant_authorization_version";
    public static final String DELEGATED_ACTOR = "act";
    public static final String ROLES = "roles";
    public static final String SCOPE = "scope";
    public static final String SESSION_ID = "sid";
    public static final String AUTHENTICATION_TIME = "auth_time";
    public static final String AUTHENTICATION_METHODS = "amr";
    public static final String IS_ASYNC = "is_async";

    private TokenClaimContract() {
    }

    public static boolean supportsVersion(Object value) {
        return value == null
                || value instanceof Number number
                && number.longValue() == CURRENT_CONTRACT_VERSION
                && number.doubleValue() == CURRENT_CONTRACT_VERSION;
    }
}
