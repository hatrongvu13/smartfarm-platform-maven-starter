package com.htv.smartfarm.identity.authorization.repository;

import java.util.Collection;
import java.util.List;

import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRoleRepository
        extends JpaRepository<MembershipRoleEntity, MembershipRoleId> {

    @Query("""
            select mr
            from MembershipRoleEntity mr
            join fetch mr.role r
            where mr.membership.id = :membershipId
            order by r.code
            """)
    List<MembershipRoleEntity> findAllWithRoleByMembershipId(
            @Param("membershipId") String membershipId
    );

    @Query("""
            select r.id
            from MembershipRoleEntity mr
            join mr.role r
            where mr.membership.id = :membershipId
            """)
    List<String> findRoleIdsByMembershipId(
            @Param("membershipId") String membershipId
    );

    @Query("""
            select r.code
            from MembershipRoleEntity mr
            join mr.role r
            where mr.membership.id = :membershipId
            order by r.code
            """)
    List<String> findRoleCodesByMembershipId(
            @Param("membershipId") String membershipId
    );

    boolean existsByMembershipIdAndRoleId(
            String membershipId,
            String roleId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MembershipRoleEntity mr
            where mr.membership.id = :membershipId
              and mr.role.id = :roleId
            """)
    int deleteByMembershipIdAndRoleId(
            @Param("membershipId") String membershipId,
            @Param("roleId") String roleId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MembershipRoleEntity mr
            where mr.membership.id = :membershipId
            """)
    int deleteAllByMembershipId(
            @Param("membershipId") String membershipId
    );

    @Query("""
            select count(mr)
            from MembershipRoleEntity mr
            where mr.role.id in :roleIds
            """)
    long countAssignmentsByRoleIds(
            @Param("roleIds") Collection<String> roleIds
    );
}