package com.htv.smartfarm.security.grpc;
import io.grpc.Context;
import java.util.Set;
public final class GrpcSecurityContext {
 private GrpcSecurityContext() {}
 public static final Context.Key<String> SUBJECT=Context.key("smartfarm-subject");
 public static final Context.Key<String> TENANT=Context.key("smartfarm-tenant");
 public static final Context.Key<String> CORRELATION_ID=Context.key("smartfarm-correlation-id");
 public static final Context.Key<Set<String>> AUTHORITIES=Context.key("smartfarm-authorities");
}
