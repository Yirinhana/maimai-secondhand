package com.maimai.identity.repo;

import com.maimai.identity.domain.UserRole;
import com.maimai.identity.domain.UserRole.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    boolean existsByUserIdAndRole(Long userId, Role role);
}
