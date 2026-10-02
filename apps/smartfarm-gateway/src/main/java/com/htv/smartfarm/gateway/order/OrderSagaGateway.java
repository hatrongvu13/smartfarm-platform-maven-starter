package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcExceptionMapper;
import com.htv.smartfarm.gateway.identity.GatewayRequestContextFactory;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaGateway {
    private static final String AUDIENCE="smartfarm-order";
    private final OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub base;
    private final ServiceTokenClient tokens; private final GatewayRequestContextFactory contexts;
    private final GatewayGrpcExceptionMapper errors;
    public OrderSagaGateway(OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub base,
            ServiceTokenClient tokens, GatewayRequestContextFactory contexts, GatewayGrpcExceptionMapper errors) {
        this.base=base; this.tokens=tokens; this.contexts=contexts; this.errors=errors;
    }
    public Map<String,Object> inspect(Jwt jwt,String sagaId){ var c=contexts.create(jwt); return graph("GetOrderSaga",jwt,()->view(stub(jwt,c.getCorrelationId()).getOrderSaga(GetOrderSagaRequest.newBuilder().setContext(c).setSagaId(sagaId).build()).getSaga())); }
    public Map<String,Object> retry(Jwt jwt,String sagaId,String stepKey,String reason){ var c=contexts.create(jwt); return graph("RetryOrderSagaStep",jwt,()->view(stub(jwt,c.getCorrelationId()).retryOrderSagaStep(RetryOrderSagaStepRequest.newBuilder().setContext(c).setSagaId(sagaId).setStepKey(stepKey).setReason(reason).build()).getSaga())); }
    public Map<String,Object> action(Jwt jwt,String operation,String sagaId,String reason){ var c=contexts.create(jwt); var r=OrderSagaActionRequest.newBuilder().setContext(c).setSagaId(sagaId).setReason(reason).build(); return graph(operation,jwt,()->view(switch(operation){ case "ResumeOrderSaga"->stub(jwt,c.getCorrelationId()).resumeOrderSaga(r).getSaga(); case "ForceCompensateOrder"->stub(jwt,c.getCorrelationId()).forceCompensateOrder(r).getSaga(); case "ForceCancelOrder"->stub(jwt,c.getCorrelationId()).forceCancelOrder(r).getSaga(); case "ForceCompleteOrder"->stub(jwt,c.getCorrelationId()).forceCompleteOrder(r).getSaga(); case "MarkOrderSagaResolved"->stub(jwt,c.getCorrelationId()).markOrderSagaResolved(r).getSaga(); default->throw new IllegalArgumentException("unsupported saga action"); })); }
    public Map<String,Object> restInspect(Jwt jwt,String sagaId){ try{return inspectRaw(jwt,sagaId);}catch(StatusRuntimeException e){throw errors.rest("GetOrderSaga",e);} }
    public Map<String,Object> restRetry(Jwt jwt,String sagaId,String stepKey,String reason){ try{return retryRaw(jwt,sagaId,stepKey,reason);}catch(StatusRuntimeException e){throw errors.rest("RetryOrderSagaStep",e);} }
    public Map<String,Object> restAction(Jwt jwt,String op,String sagaId,String reason){ try{return actionRaw(jwt,op,sagaId,reason);}catch(StatusRuntimeException e){throw errors.rest(op,e);} }
    private Map<String,Object> inspectRaw(Jwt jwt,String id){var c=contexts.create(jwt);return view(stub(jwt,c.getCorrelationId()).getOrderSaga(GetOrderSagaRequest.newBuilder().setContext(c).setSagaId(id).build()).getSaga());}
    private Map<String,Object> retryRaw(Jwt jwt,String id,String step,String reason){var c=contexts.create(jwt);return view(stub(jwt,c.getCorrelationId()).retryOrderSagaStep(RetryOrderSagaStepRequest.newBuilder().setContext(c).setSagaId(id).setStepKey(step).setReason(reason).build()).getSaga());}
    private Map<String,Object> actionRaw(Jwt jwt,String op,String id,String reason){var c=contexts.create(jwt);var r=OrderSagaActionRequest.newBuilder().setContext(c).setSagaId(id).setReason(reason).build();return view(switch(op){case "ResumeOrderSaga"->stub(jwt,c.getCorrelationId()).resumeOrderSaga(r).getSaga();case "ForceCompensateOrder"->stub(jwt,c.getCorrelationId()).forceCompensateOrder(r).getSaga();case "ForceCancelOrder"->stub(jwt,c.getCorrelationId()).forceCancelOrder(r).getSaga();case "ForceCompleteOrder"->stub(jwt,c.getCorrelationId()).forceCompleteOrder(r).getSaga();case "MarkOrderSagaResolved"->stub(jwt,c.getCorrelationId()).markOrderSagaResolved(r).getSaga();default->throw new IllegalArgumentException("unsupported saga action");});}
    private <T>T graph(String op,Jwt jwt,java.util.concurrent.Callable<T> action){try{return action.call();}catch(StatusRuntimeException e){if(e.getStatus().getCode()==io.grpc.Status.Code.UNAUTHENTICATED)tokens.invalidate(AUDIENCE,jwt.getClaimAsString("tenant_id"));throw errors.graphQl(op,e);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}}
    private OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub stub(Jwt jwt,String correlation){String tenant=jwt.getClaimAsString("tenant_id");String token=tokens.tokenFor(AUDIENCE,tenant,jwt.getSubject());return base.withDeadlineAfter(5,TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(()->token,()->tenant,()->correlation));}
    public static Map<String,Object> view(OrderSaga s){Map<String,Object> m=new LinkedHashMap<>();m.put("sagaId",s.getSagaId());m.put("orderId",s.getOrderId());m.put("status",s.getStatus());m.put("terminalIntent",blank(s.getTerminalIntent()));m.put("currentStepKey",blank(s.getCurrentStepKey()));m.put("attemptCount",s.getAttemptCount());m.put("nextAttemptAt",s.hasNextAttemptAt()?ts(s.getNextAttemptAt()):null);m.put("claimedAt",s.hasClaimedAt()?ts(s.getClaimedAt()):null);m.put("compensationDeadlineAt",s.hasCompensationDeadlineAt()?ts(s.getCompensationDeadlineAt()):null);m.put("lastErrorCode",blank(s.getLastErrorCode()));m.put("lastErrorMessage",blank(s.getLastErrorMessage()));m.put("steps",s.getStepsList().stream().map(OrderSagaGateway::step).toList());return m;}
    private static Map<String,Object> step(OrderSagaStep s){Map<String,Object> m=new LinkedHashMap<>();m.put("stepKey",s.getStepKey());m.put("sequenceNo",s.getSequenceNo());m.put("stepType",s.getStepType());m.put("status",s.getStatus());m.put("orderLineId",blank(s.getOrderLineId()));m.put("externalReferenceId",blank(s.getExternalReferenceId()));m.put("attemptCount",s.getAttemptCount());m.put("nextAttemptAt",s.hasNextAttemptAt()?ts(s.getNextAttemptAt()):null);m.put("claimedAt",s.hasClaimedAt()?ts(s.getClaimedAt()):null);m.put("lastErrorCode",blank(s.getLastErrorCode()));m.put("lastErrorMessage",blank(s.getLastErrorMessage()));return m;}
    private static String ts(com.google.protobuf.Timestamp v){return Instant.ofEpochSecond(v.getSeconds(),v.getNanos()).toString();} private static String blank(String v){return v==null||v.isBlank()?null:v;}
}
