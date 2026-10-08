package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcExceptionMapper;
import com.htv.smartfarm.gateway.identity.GatewayRequestContextFactory;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaGateway {
    private static final String AUDIENCE="smartfarm-order";
    private final OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub base;
    private final ServiceTokenClient tokens; private final GatewayRequestContextFactory contexts;
    private final GatewayGrpcExceptionMapper errors; private final long deadlineMillis;
    public OrderSagaGateway(OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub base,
            ServiceTokenClient tokens, GatewayRequestContextFactory contexts, GatewayGrpcExceptionMapper errors, @Value("${smartfarm.gateway.grpc.saga-admin-deadline:5s}") Duration deadline) {
        this.base=base; this.tokens=tokens; this.contexts=contexts; this.errors=errors; this.deadlineMillis=positive(deadline);
    }
    public Map<String,Object> inspect(Jwt jwt,String correlationId,String sagaId){ var c=contexts.create(jwt,correlationId,null); return graph("GetOrderSaga",jwt,()->view(stub(jwt,c.getCorrelationId()).getOrderSaga(GetOrderSagaRequest.newBuilder().setContext(c).setSagaId(required(sagaId,"sagaId")).build()).getSaga())); }
    public Map<String,Object> inspectByOrder(Jwt jwt,String correlationId,String orderId){ var c=contexts.create(jwt,correlationId,null); return graph("GetOrderSagaByOrder",jwt,()->view(stub(jwt,c.getCorrelationId()).getOrderSagaByOrder(GetOrderSagaByOrderRequest.newBuilder().setContext(c).setOrderId(required(orderId,"orderId")).build()).getSaga())); }
    public Map<String,Object> retry(Jwt jwt,String correlationId,String sagaId,String stepKey,String reason){ var c=contexts.create(jwt,correlationId,null); return graph("RetryOrderSagaStep",jwt,()->view(stub(jwt,c.getCorrelationId()).retryOrderSagaStep(RetryOrderSagaStepRequest.newBuilder().setContext(c).setSagaId(sagaId).setStepKey(stepKey).setReason(reason).build()).getSaga())); }
    public Map<String,Object> action(Jwt jwt,String correlationId,String operation,String sagaId,String reason){ var c=contexts.create(jwt,correlationId,null); var r=OrderSagaActionRequest.newBuilder().setContext(c).setSagaId(sagaId).setReason(reason).build(); return graph(operation,jwt,()->view(switch(operation){ case "ResumeOrderSaga"->stub(jwt,c.getCorrelationId()).resumeOrderSaga(r).getSaga(); case "ForceCompensateOrder"->stub(jwt,c.getCorrelationId()).forceCompensateOrder(r).getSaga(); case "ForceCancelOrder"->stub(jwt,c.getCorrelationId()).forceCancelOrder(r).getSaga(); case "ForceCompleteOrder"->stub(jwt,c.getCorrelationId()).forceCompleteOrder(r).getSaga(); case "MarkOrderSagaResolved"->stub(jwt,c.getCorrelationId()).markOrderSagaResolved(r).getSaga(); default->throw new IllegalArgumentException("unsupported saga action"); })); }
    public Map<String,Object> restInspect(Jwt jwt,String correlationId,String sagaId){ try{return inspectRaw(jwt,correlationId,sagaId);}catch(StatusRuntimeException e){throw errors.rest("GetOrderSaga",e);} }
    public Map<String,Object> restInspectByOrder(Jwt jwt,String correlationId,String orderId){ try{return inspectByOrderRaw(jwt,correlationId,orderId);}catch(StatusRuntimeException e){throw errors.rest("GetOrderSagaByOrder",e);} }
    public Map<String,Object> restRetry(Jwt jwt,String correlationId,String sagaId,String stepKey,String reason){ try{return retryRaw(jwt,correlationId,sagaId,stepKey,reason);}catch(StatusRuntimeException e){throw errors.rest("RetryOrderSagaStep",e);} }
    public Map<String,Object> restAction(Jwt jwt,String correlationId,String op,String sagaId,String reason){ try{return actionRaw(jwt,correlationId,op,sagaId,reason);}catch(StatusRuntimeException e){throw errors.rest(op,e);} }
    private Map<String,Object> inspectRaw(Jwt jwt,String correlationId,String id){var c=contexts.create(jwt,correlationId,null);return view(stub(jwt,c.getCorrelationId()).getOrderSaga(GetOrderSagaRequest.newBuilder().setContext(c).setSagaId(required(id,"sagaId")).build()).getSaga());}
    private Map<String,Object> inspectByOrderRaw(Jwt jwt,String correlationId,String orderId){var c=contexts.create(jwt,correlationId,null);return view(stub(jwt,c.getCorrelationId()).getOrderSagaByOrder(GetOrderSagaByOrderRequest.newBuilder().setContext(c).setOrderId(required(orderId,"orderId")).build()).getSaga());}
    private Map<String,Object> retryRaw(Jwt jwt,String correlationId,String id,String step,String reason){var c=contexts.create(jwt,correlationId,null);return view(stub(jwt,c.getCorrelationId()).retryOrderSagaStep(RetryOrderSagaStepRequest.newBuilder().setContext(c).setSagaId(id).setStepKey(step).setReason(reason).build()).getSaga());}
    private Map<String,Object> actionRaw(Jwt jwt,String correlationId,String op,String id,String reason){var c=contexts.create(jwt,correlationId,null);var r=OrderSagaActionRequest.newBuilder().setContext(c).setSagaId(id).setReason(reason).build();return view(switch(op){case "ResumeOrderSaga"->stub(jwt,c.getCorrelationId()).resumeOrderSaga(r).getSaga();case "ForceCompensateOrder"->stub(jwt,c.getCorrelationId()).forceCompensateOrder(r).getSaga();case "ForceCancelOrder"->stub(jwt,c.getCorrelationId()).forceCancelOrder(r).getSaga();case "ForceCompleteOrder"->stub(jwt,c.getCorrelationId()).forceCompleteOrder(r).getSaga();case "MarkOrderSagaResolved"->stub(jwt,c.getCorrelationId()).markOrderSagaResolved(r).getSaga();default->throw new IllegalArgumentException("unsupported saga action");});}
    private <T>T graph(String op,Jwt jwt,java.util.concurrent.Callable<T> action){try{return action.call();}catch(StatusRuntimeException e){if(e.getStatus().getCode()==io.grpc.Status.Code.UNAUTHENTICATED)tokens.invalidate(AUDIENCE,jwt.getClaimAsString("tenant_id"));throw errors.graphQl(op,e);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}}
    private OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub stub(Jwt jwt,String correlation){String tenant=jwt.getClaimAsString("tenant_id");String token=tokens.tokenFor(AUDIENCE,tenant,jwt.getSubject());return base.withDeadlineAfter(deadlineMillis,TimeUnit.MILLISECONDS).withCallCredentials(new BearerCallCredentials(()->token,()->tenant,()->correlation));}
    private static long positive(Duration v){if(v==null||v.isZero()||v.isNegative())throw new IllegalArgumentException("saga admin deadline must be positive");return v.toMillis();}
    public static Map<String,Object> view(OrderSaga s){Map<String,Object> m=new LinkedHashMap<>();m.put("sagaId",s.getSagaId());m.put("orderId",s.getOrderId());m.put("status",s.getStatus());m.put("terminalIntent",blank(s.getTerminalIntent()));m.put("currentStepKey",blank(s.getCurrentStepKey()));m.put("attemptCount",s.getAttemptCount());m.put("nextAttemptAt",s.hasNextAttemptAt()?ts(s.getNextAttemptAt()):null);m.put("claimedAt",s.hasClaimedAt()?ts(s.getClaimedAt()):null);m.put("compensationDeadlineAt",s.hasCompensationDeadlineAt()?ts(s.getCompensationDeadlineAt()):null);m.put("lastErrorCode",blank(s.getLastErrorCode()));m.put("lastErrorMessage",blank(s.getLastErrorMessage()));m.put("steps",s.getStepsList().stream().map(OrderSagaGateway::step).toList());return m;}
    private static Map<String,Object> step(OrderSagaStep s){Map<String,Object> m=new LinkedHashMap<>();m.put("stepKey",s.getStepKey());m.put("sequenceNo",s.getSequenceNo());m.put("stepType",s.getStepType());m.put("status",s.getStatus());m.put("orderLineId",blank(s.getOrderLineId()));m.put("externalReferenceId",blank(s.getExternalReferenceId()));m.put("attemptCount",s.getAttemptCount());m.put("nextAttemptAt",s.hasNextAttemptAt()?ts(s.getNextAttemptAt()):null);m.put("claimedAt",s.hasClaimedAt()?ts(s.getClaimedAt()):null);m.put("lastErrorCode",blank(s.getLastErrorCode()));m.put("lastErrorMessage",blank(s.getLastErrorMessage()));return m;}
    private static String ts(com.google.protobuf.Timestamp v){return Instant.ofEpochSecond(v.getSeconds(),v.getNanos()).toString();} private static String blank(String v){return v==null||v.isBlank()?null:v;} private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" is required");return v.trim();}
}
