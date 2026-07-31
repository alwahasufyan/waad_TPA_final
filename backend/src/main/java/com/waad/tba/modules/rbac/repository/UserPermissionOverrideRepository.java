package com.waad.tba.modules.rbac.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.rbac.entity.UserPermissionOverride;

@Repository
public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverride, Long> {
    List<UserPermissionOverride> findByUserId(Long userId);
    List<UserPermissionOverride> findByUserIdAndRevokedAtIsNull(Long userId);
}
