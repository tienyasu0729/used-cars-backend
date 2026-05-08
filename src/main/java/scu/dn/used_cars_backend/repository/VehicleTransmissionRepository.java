package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.VehicleTransmission;

import java.util.List;
import java.util.Optional;

public interface VehicleTransmissionRepository extends JpaRepository<VehicleTransmission, Integer> {

	List<VehicleTransmission> findAllByOrderByNameAsc();

	List<VehicleTransmission> findByStatusIgnoreCaseOrderByNameAsc(String status);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

	Optional<VehicleTransmission> findByNameIgnoreCase(String name);
}
