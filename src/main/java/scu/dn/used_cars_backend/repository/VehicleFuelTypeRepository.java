package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.VehicleFuelType;

import java.util.List;
import java.util.Optional;

public interface VehicleFuelTypeRepository extends JpaRepository<VehicleFuelType, Integer> {

	List<VehicleFuelType> findAllByOrderByNameAsc();

	List<VehicleFuelType> findByStatusIgnoreCaseOrderByNameAsc(String status);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

	Optional<VehicleFuelType> findByNameIgnoreCase(String name);
}
