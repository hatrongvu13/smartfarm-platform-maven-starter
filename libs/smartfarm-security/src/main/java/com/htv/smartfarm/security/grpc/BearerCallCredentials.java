package com.htv.smartfarm.security.grpc;
import io.grpc.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
/** Explicit service-token supplier. Do not blindly forward a user's access token across services. */
public final class BearerCallCredentials extends CallCredentials {
 private static final Metadata.Key<String> AUTH=Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER);
 private final Supplier<String> tokenSupplier;
 public BearerCallCredentials(Supplier<String> tokenSupplier) { this.tokenSupplier=tokenSupplier; }
 @Override public void applyRequestMetadata(RequestInfo info, Executor executor, MetadataApplier applier) {
  executor.execute(() -> {
   try {
    String token=tokenSupplier.get();
    if (token==null || token.isBlank()) { applier.fail(Status.UNAUTHENTICATED);return; }
    Metadata metadata=new Metadata();metadata.put(AUTH,"Bearer "+token);applier.apply(metadata);
   } catch (RuntimeException ex) { applier.fail(Status.UNAUTHENTICATED); }
  });
 }
 @Override public void thisUsesUnstableApi() {}
}
