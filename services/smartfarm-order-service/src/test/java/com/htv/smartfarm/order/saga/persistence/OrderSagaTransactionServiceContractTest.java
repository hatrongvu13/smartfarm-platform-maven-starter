package com.htv.smartfarm.order.saga.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class OrderSagaTransactionServiceContractTest {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void graphUsesStableStepOrderAndIdempotencyKeys() {
        OrderSagaJpaRepository sagas = mock(OrderSagaJpaRepository.class);
        OrderSagaStepJpaRepository steps = mock(OrderSagaStepJpaRepository.class);
        OrderLineJpaRepository lines = mock(OrderLineJpaRepository.class);
        OrderSagaInsertService inserts = mock(OrderSagaInsertService.class);
        when(sagas.findByTenantIdAndOrderId("tenant-1", "order-1")).thenReturn(Optional.empty());
        when(lines.findByOrderIdOrderByLineNo("order-1")).thenReturn(List.of(
                line("line-a", 0), line("line-b", 1)));

        service(sagas, steps, lines, inserts).create("tenant-1", "order-1", "actor-1", "corr-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderSagaStepEntity>> graph = ArgumentCaptor.forClass(List.class);
        verify(inserts).insert(any(OrderSagaEntity.class), graph.capture());
        assertThat(graph.getValue()).extracting(OrderSagaStepEntity::getStepKey)
                .containsExactly("reserve:0", "reserve:1", "finance", "commit:0", "commit:1",
                        "reverse-finance", "release:1", "release:0");
        assertThat(graph.getValue()).extracting(OrderSagaStepEntity::getIdempotencyKey)
                .containsExactly("order-1:reserve:0", "order-1:reserve:1", "order-1:finance",
                        "order-1:commit:0", "order-1:commit:1", "order-1:reverse-finance",
                        "order-1:release:1", "order-1:release:0");
    }

    @Test
    void operatorStaleRecoveryIsRestrictedToRequestedTenant() {
        OrderSagaJpaRepository sagas = mock(OrderSagaJpaRepository.class);
        OrderSagaStepJpaRepository steps = mock(OrderSagaStepJpaRepository.class);
        OrderLineJpaRepository lines = mock(OrderLineJpaRepository.class);
        OrderSagaInsertService inserts = mock(OrderSagaInsertService.class);
        when(sagas.lockStaleByTenantId(any(), anyList(), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        int recovered = service(sagas, steps, lines, inserts).recoverStaleClaims("tenant-1");

        assertThat(recovered).isZero();
        verify(sagas).lockStaleByTenantId(
                org.mockito.ArgumentMatchers.eq("tenant-1"), anyList(), any(Instant.class), any(Pageable.class));
    }

    @Test
    void defaultsPinTheDurableSagaPolicy() {
        OrderSagaProperties defaults = new OrderSagaProperties(true, 0, 0, null, null,
                null, null, null, null);
        assertThat(defaults.batchSize()).isEqualTo(20);
        assertThat(defaults.maximumAttempts()).isEqualTo(5);
        assertThat(defaults.initialRetry()).isEqualTo(Duration.ofSeconds(5));
        assertThat(defaults.maximumRetry()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.claimTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(defaults.processingDeadline()).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.financeManualReviewWindow()).isEqualTo(Duration.ofMinutes(60));
        assertThat(defaults.compensationDeadline()).isEqualTo(Duration.ofHours(6));
    }

    private OrderSagaTransactionService service(OrderSagaJpaRepository sagas,
                                                OrderSagaStepJpaRepository steps, OrderLineJpaRepository lines,
                                                OrderSagaInsertService inserts) {
        return new OrderSagaTransactionService(sagas, steps, lines,
                new OrderSagaProperties(true, 20, 5, Duration.ofSeconds(5),
                        Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofMinutes(30),
                        Duration.ofMinutes(60), Duration.ofHours(6)),
                inserts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OrderLineEntity line(String id, int lineNo) {
        return new OrderLineEntity(id, "order-1", lineNo, "item-" + lineNo,
                "warehouse-1", "1", "KG", 1000, 1000);
    }
}
