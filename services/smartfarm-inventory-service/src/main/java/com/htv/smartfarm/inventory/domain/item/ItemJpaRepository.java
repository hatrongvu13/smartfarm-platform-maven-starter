package com.htv.smartfarm.inventory.domain.item;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped item persistence and catalog queries. */
public interface ItemJpaRepository extends JpaRepository<ItemEntity, String> {

    Optional<ItemEntity> findByTenantIdAndId(String tenantId, String id);

    Optional<ItemEntity> findByTenantIdAndSku(String tenantId, String sku);

    @Query("""
            select i
            from ItemEntity i
            where i.tenantId = :tenant
              and (
                    :query = ''
                    or lower(i.sku) like lower(concat('%', :query, '%'))
                    or lower(i.name) like lower(concat('%', :query, '%'))
                  )
            order by i.sku, i.id
            """)
    Page<ItemEntity> search(
            @Param("tenant") String tenant,
            @Param("query") String query,
            Pageable page
    );

    @Query("""
            select i
            from ItemEntity i
            where i.tenantId = :tenant
              and i.category = :category
              and (
                    :query = ''
                    or lower(i.sku) like lower(concat('%', :query, '%'))
                    or lower(i.name) like lower(concat('%', :query, '%'))
                  )
            order by i.sku, i.id
            """)
    Page<ItemEntity> searchByCategory(
            @Param("tenant") String tenant,
            @Param("query") String query,
            @Param("category") ItemCategoryValue category,
            Pageable page
    );
}
