package com.htv.farmverse.common.contract;

public final class IdentityEventContract {

    public static final String TOPIC_NAMESPACE = "farmverse";
    public static final String DOMAIN_EVENT_SEGMENT = "domain";
    public static final String TOPIC_VERSION_PREFIX = "v";
    public static final String GLOBAL_FARM_ID = "_global";
    public static final String IDENTITY_PRODUCER = "identity-service";
    public static final String AUTHORIZATION_INVALIDATED_EVENT_TYPE =
            "identity.authorization.invalidated";
    public static final String AUTHORIZATION_INVALIDATED_EVENT_NAME =
            "authorization-invalidated";
    public static final String AUTHORIZATION_TENANT_AGGREGATE_TYPE =
            "authorization-tenant";
    public static final String AUTHORIZATION_SUBJECT_AGGREGATE_TYPE =
            "authorization-subject";

    private IdentityEventContract() {
    }
}
