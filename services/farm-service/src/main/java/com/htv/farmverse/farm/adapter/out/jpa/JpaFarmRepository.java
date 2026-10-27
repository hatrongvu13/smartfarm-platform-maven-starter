package com.htv.farmverse.farm.adapter.out.jpa;

import java.util.List;
import java.util.Optional;

import com.htv.farmverse.farm.application.FarmRepository;
import com.htv.farmverse.farm.domain.Barn;
import com.htv.farmverse.farm.domain.Farm;

import org.springframework.stereotype.Repository;

/** Adapter: nối port {@link FarmRepository} với Spring Data JPA, đổi qua lại giữa domain và entity. */
@Repository
class JpaFarmRepository implements FarmRepository {

    private final FarmJpaRepository jpa;

    JpaFarmRepository(FarmJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Farm> findById(String id) {
        return jpa.findById(id).map(JpaFarmRepository::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsByCode(code);
    }

    @Override
    public List<Farm> findAll() {
        return jpa.findAllByOrderByCode().stream().map(JpaFarmRepository::toDomain).toList();
    }

    @Override
    public Farm save(Farm farm) {
        FarmEntity entity = jpa.findById(farm.id()).orElseGet(() -> new FarmEntity(farm.id()));
        entity.setCode(farm.code());
        entity.setName(farm.name());
        entity.setAddress(farm.address());
        entity.setStatus(farm.status());
        entity.getBarns().clear();
        farm.barns().forEach(b -> entity.getBarns().add(new BarnEmbeddable(b.code(), b.name(), b.capacity())));
        return toDomain(jpa.saveAndFlush(entity));
    }

    private static Farm toDomain(FarmEntity e) {
        List<Barn> barns = e.getBarns().stream().map(b -> new Barn(b.getCode(), b.getName(), b.getCapacity())).toList();
        return Farm.restore(e.getId(), e.getCode(), e.getName(), e.getAddress(), e.getStatus(), barns, e.getVersion());
    }
}
