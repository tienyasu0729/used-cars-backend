package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.RolePermission;
import scu.dn.used_cars_backend.entity.RolePermissionId;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

	void deleteAllByRole_Id(Integer roleId);

	List<RolePermission> findAllByRole_IdOrderByPermission_IdAsc(Integer roleId);
}
