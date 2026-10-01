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
            select mf.id.farmId
            from MembershipFarmEntity mf
            where mf.id.membershipId = :membershipId
            order by mf.id.farmId
            """)
    List<String> findFarmIdsByMembershipId(
            @Param("membershipId") String membershipId
    );

    boolean existsByIdMembershipIdAndIdFarmId(
            String membershipId,
            String farmId
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            delete from MembershipFarmEntity mf
            where mf.id.membershipId = :membershipId
              and mf.id.farmId = :farmId
            """)
    int deleteByMembershipIdAndFarmId(
            @Param("membershipId") String membershipId,
            @Param("farmId") String farmId
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            delete from MembershipFarmEntity mf
            where mf.id.membershipId = :membershipId
            """)
    int deleteAllByMembershipId(
            @Param("membershipId") String membershipId
    );
}
