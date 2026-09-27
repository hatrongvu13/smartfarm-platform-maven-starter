package com.htv.smartfarm.finance.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, String> {
    Optional<TransactionEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<TransactionEntity> findByTenantIdAndId(String tenantId, String id);

    @Query("""
            select t from TransactionEntity t
            where t.tenantId = :tenant and t.farmId = :farm
              and (:batch is null or t.batchId = :batch)
              and (:from is null or t.occurredAt >= :from)
              and (:to is null or t.occurredAt <= :to)
            order by t.occurredAt desc, t.id
            """)
    List<TransactionEntity> list(@Param("tenant") String tenant, @Param("farm") String farm,
                                 @Param("batch") String batch, @Param("from") Long from, @Param("to") Long to,
                                 Pageable page);

    /** Sum of minor units for a kind + optional category within a batch (for batch cost breakdown). */
    @Query("""
            select coalesce(sum(t.minorUnits), 0) from TransactionEntity t
            where t.tenantId = :tenant and t.farmId = :farm and t.batchId = :batch
              and t.kind = :kind and (:category is null or t.category = :category)
            """)
    long sumByKindAndCategory(@Param("tenant") String tenant, @Param("farm") String farm, @Param("batch") String batch,
                              @Param("kind") String kind, @Param("category") String category);

    /** Sum of minor units for a kind over a farm + optional period (for cash flow). */
    @Query("""
            select coalesce(sum(t.minorUnits), 0) from TransactionEntity t
            where t.tenantId = :tenant and t.farmId = :farm and t.kind = :kind
              and (:from is null or t.occurredAt >= :from)
              and (:to is null or t.occurredAt <= :to)
            """)
    long sumByKind(@Param("tenant") String tenant, @Param("farm") String farm, @Param("kind") String kind,
                   @Param("from") Long from, @Param("to") Long to);

    /** First currency seen for a farm — a pragmatic single-currency assumption for aggregates. */
    @Query("""
            select t.currencyCode from TransactionEntity t
            where t.tenantId = :tenant and t.farmId = :farm order by t.occurredAt
            """)
    List<String> currencies(@Param("tenant") String tenant, @Param("farm") String farm, Pageable page);
}
