package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.BranchOpeningHoursProvider;
import scu.dn.used_cars_backend.booking.dto.AvailableSlotResponse;
import scu.dn.used_cars_backend.booking.entity.BookingSlot;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.booking.repository.BookingSlotRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotAvailabilityService {

	private static final List<String> SLOT_COUNT_STATUSES = List.of("Pending", "Confirmed");

	private final BookingSlotRepository bookingSlotRepository;
	private final BookingRepository bookingRepository;
	private final BranchRepository branchRepository;
	private final VehicleRepository vehicleRepository;
	private final BranchOpeningHoursProvider openingHoursProvider;

	@Transactional(readOnly = true)
	public List<AvailableSlotResponse> getAvailableSlots(int branchId, LocalDate date, Long vehicleId) {
		if (branchRepository.findByIdAndDeletedFalse(branchId).isEmpty()) {
			throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh.");
		}
		if (vehicleId != null) {
			Vehicle v = vehicleRepository.findById(vehicleId)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
			if (v.getBranch() == null || v.getBranch().getId() == null || v.getBranch().getId() != branchId) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Xe không thuộc chi nhánh đã chọn.");
			}
		}
		List<BookingSlot> templates = bookingSlotRepository.findByBranch_IdAndActiveTrueOrderBySlotTimeAsc(branchId);
		return templates.stream()
				.filter(slot -> openingHoursProvider.isWithinWorkingHours(branchId, date, slot.getSlotTime()))
				.map(slot -> {
					long taken = bookingRepository.countAtBranchSlot(branchId, date, slot.getSlotTime(), SLOT_COUNT_STATUSES);
					int max = slot.getMaxBookings() != null ? slot.getMaxBookings() : 0;
					int branchAvailable = (int) Math.max(0, max - taken);
					int available = branchAvailable;
					if (vehicleId != null && branchAvailable > 0) {
						long vTaken = bookingRepository.countAtVehicleSlot(vehicleId, date, slot.getSlotTime(),
								SLOT_COUNT_STATUSES);
						if (vTaken > 0) {
							available = 0;
						}
					}
					return AvailableSlotResponse.builder()
							.slotTime(slot.getSlotTime())
							.availableCount(available)
							.maxBookings(max)
							.build();
				})
				.toList();
	}
}
