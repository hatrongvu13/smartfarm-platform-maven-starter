package com.htv.smartfarm.identity.authorization.repository;

import java.util.List;

import com.htv.smartfarm.identity.authorization.domain.MembershipFarmEntity;
import com.htv.smartfarm.identity.authorization.domain.MembershipFarmId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipFarmRepository
        extends JpaRepository<MembershipFarmEntity, MembershipFarmId> {

    @Query("""
            select mf.farmId
            from MembershipFarmEntity mf
            where mf.membership.id = :membershipId
            order by mf.farmId
            """)
    List<String> findFarmIdsByMembershipId(
            @Param("membershipId") String membershipId
    );

    boolean existsByMembershipIdAndFarmId(
            String membershipId,
            String farmId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MembershipFarmEntity mf
            where mf.membership.id = :membershipId
              and mf.farmId = :farmId
            """)
    int deleteByMembershipIdAndFarmId(
            @Param("membershipId") String membershipId,
            @Param("farmId") String farmId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MembershipFarmEntity mf
            where mf.membership.id = :membershipId
            """)
    int deleteAllByMembershipId(
            @Param("membershipId") String membershipId
    );
}