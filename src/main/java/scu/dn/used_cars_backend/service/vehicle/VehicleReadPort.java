package scu.dn.used_cars_backend.service.vehicle;

import scu.dn.used_cars_backend.entity.Vehicle;

import java.util.List;

/** Port đọc xe (Dev 2) — Tier 3.1 chỉ gọi interface này, không tự viết query vào Vehicles. */
public interface VehicleReadPort {

	boolean existsForCustomerSave(long vehicleId);

	List<InteractionVehicleSnapshot> findSnapshotsByIdsPreserveOrder(List<Long> orderedIds);

	/** Reference Hibernate (FK) cho SavedVehicles — không dùng để đọc dữ liệu hiển thị. */
	Vehicle vehicleFkReference(long vehicleId);
}
