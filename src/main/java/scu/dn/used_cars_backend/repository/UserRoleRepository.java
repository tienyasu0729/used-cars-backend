package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.entity.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

	void deleteAllByUser_Id(Long userId);

	long countByRole_Id(Integer roleId);
}
