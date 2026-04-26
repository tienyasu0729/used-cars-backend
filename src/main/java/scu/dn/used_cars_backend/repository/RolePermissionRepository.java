package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.RolePermission;
import scu.dn.used_cars_backend.entity.RolePermissionId;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

	void deleteAllByRole_Id(Integer roleId);

	List<RolePermission> findAllByRole_IdOrderByPermission_IdAsc(Integer roleId);

	@Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE UPPER(rp.role.name) = UPPER(:roleName)")
	List<RolePermission> findAllByRole_NameIgnoreCaseWithPermission(@Param("roleName") String roleName);
}
