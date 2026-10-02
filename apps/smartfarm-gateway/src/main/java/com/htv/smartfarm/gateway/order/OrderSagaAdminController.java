package com.htv.smartfarm.gateway.order;
import java.util.Map;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.htv.smartfarm.gateway.context.GatewayCorrelationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@RestController @RequestMapping("/api/v1/order-sagas") @Tag(name="Order Saga Administration", description="Privileged inspection and manual recovery operations requiring orders:saga:admin") @PreAuthorize("hasAuthority('SCOPE_orders:saga:admin')")
public class OrderSagaAdminController {
 public record Reason(String reason){} private final OrderSagaGateway gateway; public OrderSagaAdminController(OrderSagaGateway gateway){this.gateway=gateway;}
 @GetMapping("/{id}") public Mono<Map<String,Object>> get(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id){return call(()->gateway.restInspect(jwt,corr(correlation),id));}
 @PostMapping("/{id}/steps/{step}/retry") public Mono<Map<String,Object>> retry(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@PathVariable String step,@RequestBody Reason body){return call(()->gateway.restRetry(jwt,corr(correlation),id,step,reason(body)));}
 @PostMapping("/{id}/resume") public Mono<Map<String,Object>> resume(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@RequestBody Reason body){return action(jwt,correlation,"ResumeOrderSaga",id,body);}
 @PostMapping("/{id}/force-compensate") public Mono<Map<String,Object>> compensate(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@RequestBody Reason body){return action(jwt,correlation,"ForceCompensateOrder",id,body);}
 @PostMapping("/{id}/force-cancel") public Mono<Map<String,Object>> cancel(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@RequestBody Reason body){return action(jwt,correlation,"ForceCancelOrder",id,body);}
 @PostMapping("/{id}/force-complete") public Mono<Map<String,Object>> complete(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@RequestBody Reason body){return action(jwt,correlation,"ForceCompleteOrder",id,body);}
 @PostMapping("/{id}/mark-resolved") public Mono<Map<String,Object>> resolved(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-Correlation-Id",required=false) String correlation,@PathVariable String id,@RequestBody Reason body){return action(jwt,correlation,"MarkOrderSagaResolved",id,body);}
 private Mono<Map<String,Object>> action(Jwt jwt,String correlation,String op,String id,Reason body){return call(()->gateway.restAction(jwt,corr(correlation),op,id,reason(body)));} private String corr(String v){return GatewayCorrelationContext.normalize(v);} private String reason(Reason b){if(b==null||b.reason()==null||b.reason().isBlank())throw new IllegalArgumentException("reason is required");return b.reason().trim();} private <T>Mono<T> call(java.util.concurrent.Callable<T> c){return Mono.fromCallable(c).subscribeOn(Schedulers.boundedElastic());}
}
