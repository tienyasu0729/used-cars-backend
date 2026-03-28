package scu.dn.used_cars_backend.repository;

// Truy vấn SavedVehicles — lưu / bỏ lưu xe của user.

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.SavedVehicle;
import scu.dn.used_cars_backend.entity.SavedVehicleId;

public interface SavedVehicleRepository extends JpaRepository<SavedVehicle, SavedVehicleId> {

	boolean existsByUser_IdAndVehicle_Id(Long userId, Long vehicleId);

}
