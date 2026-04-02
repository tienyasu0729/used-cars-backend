package scu.dn.used_cars_backend.repository;

// Repository quản lý bảng VehicleImages — tìm ảnh theo xe, xóa ảnh theo ID.

import org.springframework.data.jpa.repository.JpaRepository;
import scu.dn.used_cars_backend.entity.VehicleImage;

import java.util.Optional;

public interface VehicleImageRepository extends JpaRepository<VehicleImage, Long> {

	/** Tìm ảnh theo ID + vehicle_id — đảm bảo ảnh thuộc đúng xe. */
	Optional<VehicleImage> findByIdAndVehicle_Id(long id, long vehicleId);
}
