package com.htv.smartfarm.gateway.order;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@RestController @RequestMapping("/api/v1/order-sagas") @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')")
public class OrderSagaAdminController {
 public record Reason(String reason){} private final OrderSagaGateway gateway; public OrderSagaAdminController(OrderSagaGateway gateway){this.gateway=gateway;}
 @GetMapping("/{id}") public Mono<Map<String,Object>> get(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return call(()->gateway.restInspect(jwt,id));}
 @PostMapping("/{id}/steps/{step}/retry") public Mono<Map<String,Object>> retry(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@PathVariable String step,@RequestBody Reason body){return call(()->gateway.restRetry(jwt,id,step,reason(body)));}
 @PostMapping("/{id}/resume") public Mono<Map<String,Object>> resume(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody Reason body){return action(jwt,"ResumeOrderSaga",id,body);}
 @PostMapping("/{id}/force-compensate") public Mono<Map<String,Object>> compensate(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody Reason body){return action(jwt,"ForceCompensateOrder",id,body);}
 @PostMapping("/{id}/force-cancel") public Mono<Map<String,Object>> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody Reason body){return action(jwt,"ForceCancelOrder",id,body);}
 @PostMapping("/{id}/force-complete") public Mono<Map<String,Object>> complete(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody Reason body){return action(jwt,"ForceCompleteOrder",id,body);}
 @PostMapping("/{id}/mark-resolved") public Mono<Map<String,Object>> resolved(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestBody Reason body){return action(jwt,"MarkOrderSagaResolved",id,body);}
 private Mono<Map<String,Object>> action(Jwt jwt,String op,String id,Reason body){return call(()->gateway.restAction(jwt,op,id,reason(body)));} private String reason(Reason b){if(b==null||b.reason()==null||b.reason().isBlank())throw new IllegalArgumentException("reason is required");return b.reason().trim();} private <T>Mono<T> call(java.util.concurrent.Callable<T> c){return Mono.fromCallable(c).subscribeOn(Schedulers.boundedElastic());}
}
