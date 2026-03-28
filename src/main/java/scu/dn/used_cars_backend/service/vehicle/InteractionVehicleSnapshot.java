package scu.dn.used_cars_backend.service.vehicle;

import java.math.BigDecimal;

/**
 * Snapshot read-only của xe — Tier 3 chỉ nhận dữ liệu qua port Dev 2, không query trực tiếp bảng Vehicles.
 */
public record InteractionVehicleSnapshot(Long id, String listingId, String title, BigDecimal price, String status,
		String primaryImageUrl, boolean deleted) {
}
