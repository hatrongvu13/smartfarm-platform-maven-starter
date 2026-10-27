package com.htv.farmverse.farm.application;

import java.util.List;
import java.util.Optional;

import com.htv.farmverse.farm.domain.Farm;

/**
 * Port ra ngoài (hexagonal): use case chỉ biết interface này, không biết JPA.
 * Mọi thao tác đã tự giới hạn trong tenant hiện tại (adapter lo bằng Hibernate {@code @TenantId}).
 */
public interface FarmRepository {

    Optional<Farm> findById(String id);

    boolean existsByCode(String code);

    List<Farm> findAll();

    Farm save(Farm farm);
}
