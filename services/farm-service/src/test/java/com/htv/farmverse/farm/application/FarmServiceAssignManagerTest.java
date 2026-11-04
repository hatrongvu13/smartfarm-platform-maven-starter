package com.htv.farmverse.farm.application;

import java.util.List;
import java.util.Optional;

import com.htv.farmverse.farm.domain.Farm;
import com.htv.farmverse.farm.domain.FarmRuleViolation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * P3: use case gán người phụ trách, với {@link MemberDirectory} giả. Kiểm việc hỏi identity rồi mới ghi, và cách
 * xử lý từng câu trả lời: ACTIVE → lưu; không có / không ACTIVE → 422 SF-FARM-012; identity lỗi → 503 (không ghi).
 * Lời gọi gRPC thật và mTLS ở {@link com.htv.farmverse.farm.adapter.out.grpc.GrpcMemberDirectoryTest}.
 */
class FarmServiceAssignManagerTest {

    final InMemoryFarms repo = new InMemoryFarms();
    final FakeDirectory directory = new FakeDirectory();
    final FarmService service = new FarmService(repo, directory);

    String farmWith(com.htv.farmverse.farm.domain.FarmStatus status) {
        Farm farm = Farm.create("valley", "Valley", null);
        if (status == com.htv.farmverse.farm.domain.FarmStatus.ARCHIVED) {
            farm.archive();
        }
        repo.save(farm);
        return farm.id();
    }

    @Test
    void activeMemberIsAssignedAndSavedAfterTheDirectoryCheck() {
        String farmId = farmWith(com.htv.farmverse.farm.domain.FarmStatus.ACTIVE);
        directory.members.put("u1", new MemberDirectory.Member("u1", "An", MemberDirectory.Status.ACTIVE));

        Farm result = service.assignManager(farmId, "u1");

        assertThat(result.managerUserId()).isEqualTo("u1");
        assertThat(repo.findById(farmId).orElseThrow().managerUserId()).isEqualTo("u1");
        assertThat(directory.asked).containsExactly("u1");
    }

    @Test
    void nonMemberOrSuspendedIsRejectedAndNothingIsSaved() {
        String farmId = farmWith(com.htv.farmverse.farm.domain.FarmStatus.ACTIVE);
        directory.members.put("suspended", new MemberDirectory.Member("suspended", "B", MemberDirectory.Status.SUSPENDED));

        FarmRuleViolation missing = catchThrowableOfType(FarmRuleViolation.class, () -> service.assignManager(farmId, "ghost"));
        assertThat(missing.code()).isEqualTo("SF-FARM-012");
        FarmRuleViolation suspended = catchThrowableOfType(FarmRuleViolation.class, () -> service.assignManager(farmId, "suspended"));
        assertThat(suspended.code()).isEqualTo("SF-FARM-012");

        assertThat(repo.findById(farmId).orElseThrow().managerUserId()).isNull();
    }

    @Test
    void whenTheDirectoryIsUnavailableNothingIsSaved() {
        String farmId = farmWith(com.htv.farmverse.farm.domain.FarmStatus.ACTIVE);
        directory.unavailable = true;

        assertThatThrownBy(() -> service.assignManager(farmId, "u1"))
                .isInstanceOf(MemberDirectory.DirectoryUnavailable.class);
        assertThat(repo.findById(farmId).orElseThrow().managerUserId()).isNull();
    }

    @Test
    void anArchivedFarmCannotGetAManagerEvenForAnActiveMember() {
        String farmId = farmWith(com.htv.farmverse.farm.domain.FarmStatus.ARCHIVED);
        directory.members.put("u1", new MemberDirectory.Member("u1", "An", MemberDirectory.Status.ACTIVE));

        assertThat(catchThrowableOfType(FarmRuleViolation.class, () -> service.assignManager(farmId, "u1")).code())
                .isEqualTo("SF-FARM-013");
    }

    // ---- test doubles ----

    static final class FakeDirectory implements MemberDirectory {
        final java.util.Map<String, Member> members = new java.util.HashMap<>();
        final List<String> asked = new java.util.ArrayList<>();
        boolean unavailable;

        @Override
        public Optional<Member> find(String userId) {
            if (unavailable) {
                throw new DirectoryUnavailable(new RuntimeException("down"));
            }
            asked.add(userId);
            return Optional.ofNullable(members.get(userId));
        }

        @Override
        public List<Member> findAll(List<String> userIds) {
            if (unavailable) {
                throw new DirectoryUnavailable(new RuntimeException("down"));
            }
            return userIds.stream().map(members::get).filter(java.util.Objects::nonNull).toList();
        }
    }

    static final class InMemoryFarms implements FarmRepository {
        final java.util.Map<String, Farm> byId = new java.util.HashMap<>();

        @Override
        public Optional<Farm> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsByCode(String code) {
            return byId.values().stream().anyMatch(f -> f.code().equals(code));
        }

        @Override
        public List<Farm> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public Farm save(Farm farm) {
            byId.put(farm.id(), farm);
            return farm;
        }
    }
}
