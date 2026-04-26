package scu.dn.used_cars_backend.repository;

// Repository quản lý bảng VehicleMaintenanceHistory — truy vấn lịch sử bảo dưỡng.

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import scu.dn.used_cars_backend.entity.VehicleMaintenanceHistory;

public interface VehicleMaintenanceHistoryRepository extends JpaRepository<VehicleMaintenanceHistory, Long> {

	/** Lấy danh sách bảo dưỡng của xe, sắp xếp theo ngày giảm dần. */
	Page<VehicleMaintenanceHistory> findByVehicle_IdOrderByMaintenanceDateDesc(long vehicleId, Pageable pageable);
}
