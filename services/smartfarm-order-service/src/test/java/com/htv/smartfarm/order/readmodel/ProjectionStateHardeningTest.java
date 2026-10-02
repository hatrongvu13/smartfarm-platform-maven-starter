package com.htv.smartfarm.order.readmodel;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class ProjectionStateHardeningTest {
 @Test void inboxDefensivelyCopiesPayload(){
  byte[] p={1,2,3}; var row=new OrderProjectionInboxEntity("e","t","o",1,p,Instant.now());
  p[0]=9; byte[] copy=row.getPayload(); copy[1]=9;
  assertThat(row.getPayload()).containsExactly(1,2,3);
 }
 @Test void inboxRejectsInvalidVersion(){
  assertThatThrownBy(()->new OrderProjectionInboxEntity("e","t","o",0,new byte[]{1},Instant.now()))
   .isInstanceOf(IllegalArgumentException.class);
 }
}
