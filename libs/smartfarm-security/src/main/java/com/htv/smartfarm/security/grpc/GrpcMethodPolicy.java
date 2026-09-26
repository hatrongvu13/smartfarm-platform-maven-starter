package com.htv.smartfarm.security.grpc;
import java.util.*;
/** Exact full-method-name rules; unspecified methods require a valid token. */
public final class GrpcMethodPolicy {
 private final Map<String,String> scopes;
 private final Set<String> publicMethods;
 public GrpcMethodPolicy(Map<String,String> scopes, Set<String> publicMethods) {
  this.scopes=Map.copyOf(scopes); this.publicMethods=Set.copyOf(publicMethods);
 }
 public static GrpcMethodPolicy authenticatedByDefault() {
  return new GrpcMethodPolicy(Map.of(), Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
 }
 public boolean isPublic(String method) { return publicMethods.contains(method); }
 public String requiredAuthority(String method) { return scopes.get(method); }
}
