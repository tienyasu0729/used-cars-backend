package scu.dn.used_cars_backend.interaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.interaction.entity.VehicleViewHistory;
import scu.dn.used_cars_backend.interaction.repository.VehicleViewHistoryRepository;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleViewHistoryAsyncWriter {

	private final VehicleViewHistoryRepository vehicleViewHistoryRepository;

	@Async
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertAsync(String guestId, Long userId, long vehicleId) {
		try {
			// B1: ghi bản ghi lịch sử xem (guest_id luôn có, user_id có thể null)
			VehicleViewHistory row = new VehicleViewHistory();
			row.setGuestId(guestId);
			row.setUserId(userId);
			row.setVehicleId(vehicleId);
			row.setViewedAt(Instant.now());
			vehicleViewHistoryRepository.save(row);
		}
		catch (Exception e) {
			log.warn("Async VehicleViewHistory insert failed vehicleId={}: {}", vehicleId, e.getMessage());
		}
	}
}
