package com.htv.farmverse.farm.adapter.out.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmJpaRepository extends JpaRepository<FarmEntity, String> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = "barns")
    List<FarmEntity> findAllByOrderByCode();
}
