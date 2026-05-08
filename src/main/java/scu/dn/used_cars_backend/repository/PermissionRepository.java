package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.Permission;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {

	List<Permission> findAllByIdIn(Collection<Integer> ids);
}
