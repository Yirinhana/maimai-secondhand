package com.maimai.trade.repo;

import com.maimai.identity.domain.UserRole;
import com.maimai.identity.domain.UserRole.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 按角色反查用户（如发货超时提醒 SUPER_ADMIN）。UserRoleRepository 为既有文件，新增查询放这里。 */
public interface UserRoleQueryRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByRole(Role role);
}
