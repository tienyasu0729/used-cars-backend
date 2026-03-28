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
import scu.dn.used_cars_backend.repository.BranchRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotAvailabilityService {

	private static final List<String> SLOT_COUNT_STATUSES = List.of("Pending", "Confirmed");

	private final BookingSlotRepository bookingSlotRepository;
	private final BookingRepository bookingRepository;
	private final BranchRepository branchRepository;
	private final BranchOpeningHoursProvider openingHoursProvider;

	@Transactional(readOnly = true)
	public List<AvailableSlotResponse> getAvailableSlots(int branchId, LocalDate date) {
		if (branchRepository.findByIdAndDeletedFalse(branchId).isEmpty()) {
			throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh.");
		}
		List<BookingSlot> templates = bookingSlotRepository.findByBranch_IdAndActiveTrueOrderBySlotTimeAsc(branchId);
		return templates.stream()
				.filter(slot -> openingHoursProvider.isWithinWorkingHours(branchId, date, slot.getSlotTime()))
				.map(slot -> {
					long taken = bookingRepository.countAtBranchSlot(branchId, date, slot.getSlotTime(), SLOT_COUNT_STATUSES);
					int max = slot.getMaxBookings() != null ? slot.getMaxBookings() : 0;
					int available = (int) Math.max(0, max - taken);
					return AvailableSlotResponse.builder()
							.slotTime(slot.getSlotTime())
							.availableCount(available)
							.maxBookings(max)
							.build();
				})
				.toList();
	}
}
