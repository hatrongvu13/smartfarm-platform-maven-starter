package com.htv.smartfarm.order.outbox;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class OrderOutboxHardeningTest {
 private OrderOutboxEntity event(){return new OrderOutboxEntity("e","t","o",7,"order-changed.v1","c",1000L,"f",null,"ORDER_STATUS_CREATED","VND",10L,null);}
 @Test void retryKeepsIdentityAndEventuallyDies(){
  var e=event(); e.claim(Instant.parse("2026-01-01T00:00:00Z"));
  e.failed(Instant.parse("2026-01-01T00:00:05Z"),"DOWN",2);
  assertThat(e.getEventId()).isEqualTo("e"); assertThat(e.getStatus()).isEqualTo(OrderOutboxStatus.FAILED);
  e.claim(Instant.parse("2026-01-01T00:00:05Z")); e.failed(Instant.parse("2026-01-01T00:00:10Z"),"DOWN",2);
  assertThat(e.getStatus()).isEqualTo(OrderOutboxStatus.DEAD); assertThat(e.getEventId()).isEqualTo("e");
 }
 @Test void mapperPreservesAggregateIdentity(){
  var row=new OrderOutboxEvent("e","t","o",7,"order-changed.v1","c",1000L,"f",null,"ORDER_STATUS_CREATED","VND",10L,null);
  var mapped=OrderEventMapper.event(row);
  assertThat(mapped.getMetadata().getEventId()).isEqualTo("e");
  assertThat(mapped.getMetadata().getAggregateVersion()).isEqualTo(7);
 }
}
