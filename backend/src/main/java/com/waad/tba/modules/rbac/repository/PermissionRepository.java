package com.waad.tba.modules.rbac.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.waad.tba.modules.rbac.entity.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, String> {
    List<Permission> findByCriticalSecurityTrue();
}
