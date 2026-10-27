package com.htv.farmverse.farm.application;

import java.util.List;

import com.htv.farmverse.farm.domain.Farm;
import com.htv.farmverse.farm.domain.FarmRuleViolation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use case của miền trang trại. Mỗi method một transaction, luật nghiệp vụ nằm trong {@link Farm}. */
@Service
public class FarmService {

    private final FarmRepository farms;

    public FarmService(FarmRepository farms) {
        this.farms = farms;
    }

    @Transactional(readOnly = true)
    public List<Farm> list() {
        return farms.findAll();
    }

    @Transactional(readOnly = true)
    public Farm get(String id) {
        return farms.findById(id).orElseThrow(() -> new FarmNotFound(id));
    }

    @Transactional
    public Farm create(String code, String name, String address) {
        if (farms.existsByCode(code)) {
            throw new FarmRuleViolation("SF-FARM-009", "Mã farm " + code + " đã có");
        }
        return farms.save(Farm.create(code, name, address));
    }

    @Transactional
    public Farm rename(String id, String name, String address) {
        Farm farm = get(id);
        farm.rename(name, address);
        return farms.save(farm);
    }

    @Transactional
    public Farm addBarn(String farmId, String code, String name, int capacity) {
        Farm farm = get(farmId);
        farm.addBarn(code, name, capacity);
        return farms.save(farm);
    }

    @Transactional
    public Farm removeBarn(String farmId, String code) {
        Farm farm = get(farmId);
        farm.removeBarn(code);
        return farms.save(farm);
    }

    @Transactional
    public Farm archive(String id) {
        Farm farm = get(id);
        farm.archive();
        return farms.save(farm);
    }

    public static class FarmNotFound extends RuntimeException {
        public FarmNotFound(String id) {
            super("Không có farm " + id);
        }
    }
}
