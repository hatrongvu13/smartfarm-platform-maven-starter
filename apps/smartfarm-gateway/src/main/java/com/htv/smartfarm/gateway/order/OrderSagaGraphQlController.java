package com.htv.smartfarm.gateway.order;
import java.util.Map;
import com.htv.smartfarm.gateway.context.GatewayCorrelationContext;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@Controller
public class OrderSagaGraphQlController {
 private final OrderSagaGateway gateway; public OrderSagaGraphQlController(OrderSagaGateway gateway){this.gateway=gateway;}
 @QueryMapping @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:read\')") public Mono<Map<String,Object>> orderSaga(@Argument String sagaId){return call((jwt,correlationId)->gateway.inspect(jwt,correlationId,required(sagaId,"sagaId")));}
 @QueryMapping @PreAuthorize("hasAuthority(\'SCOPE_orders:saga:read\')") public Mono<Map<String,Object>> orderSagaByOrder(@Argument String orderId){return call((jwt,correlationId)->gateway.inspectByOrder(jwt,correlationId,required(orderId,"orderId")));}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> retryOrderSagaStep(@Argument Map<String,Object> input){return call((jwt,correlationId)->gateway.retry(jwt,correlationId,required(input,"sagaId"),required(input,"stepKey"),required(input,"reason")));}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> resumeOrderSaga(@Argument Map<String,Object> input){return action("ResumeOrderSaga",input);}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> forceCompensateOrder(@Argument Map<String,Object> input){return action("ForceCompensateOrder",input);}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> forceCancelOrder(@Argument Map<String,Object> input){return action("ForceCancelOrder",input);}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> forceCompleteOrder(@Argument Map<String,Object> input){return action("ForceCompleteOrder",input);}
 @MutationMapping @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')") public Mono<Map<String,Object>> markOrderSagaResolved(@Argument Map<String,Object> input){return action("MarkOrderSagaResolved",input);}
 private Mono<Map<String,Object>> action(String op,Map<String,Object> input){return call((jwt,correlationId)->gateway.action(jwt,correlationId,op,required(input,"sagaId"),required(input,"reason")));}
 private <T> Mono<T> call(CorrelatedAction<T> fn){return Mono.deferContextual(ctx->ReactiveSecurityContextHolder.getContext().map(c->(Jwt)c.getAuthentication().getPrincipal()).map(jwt->fn.apply(jwt,GatewayCorrelationContext.get(ctx)))).subscribeOn(Schedulers.boundedElastic());} @FunctionalInterface private interface CorrelatedAction<T>{T apply(Jwt jwt,String correlationId);}
 private String required(Map<String,Object> input,String k){return required(input==null||input.get(k)==null?null:String.valueOf(input.get(k)),k);} private String required(String v,String k){if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v.trim();}
}
