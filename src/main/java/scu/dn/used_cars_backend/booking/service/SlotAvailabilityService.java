package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.BookingSlotCounting;
import scu.dn.used_cars_backend.booking.BranchOpeningHoursProvider;
import scu.dn.used_cars_backend.booking.dto.AvailableSlotResponse;
import scu.dn.used_cars_backend.booking.entity.BookingSlot;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.booking.repository.BookingSlotRepository;
import scu.dn.used_cars_backend.common.BranchPublicAccessSupport;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotAvailabilityService {
	private static final String REASON_BRANCH_CLOSED = "BRANCH_CLOSED";
	private static final String REASON_OUTSIDE_WORKING_HOURS = "OUTSIDE_WORKING_HOURS";
	private static final String REASON_FULL = "FULL";
	private static final String REASON_VEHICLE_CONFLICT = "VEHICLE_CONFLICT";
	private static final String REASON_PAST_TIME = "PAST_TIME";

	private final BookingSlotRepository bookingSlotRepository;
	private final BookingRepository bookingRepository;
	private final BranchRepository branchRepository;
	private final VehicleRepository vehicleRepository;
	private final BranchOpeningHoursProvider openingHoursProvider;

	@Transactional(readOnly = true)
	public List<AvailableSlotResponse> getAvailableSlots(int branchId, LocalDate date, Long vehicleId) {
		Branch branch = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		boolean branchTemporarilyClosed = !BranchPublicAccessSupport.isPubliclyAccessible(branch);
		if (vehicleId != null) {
			Vehicle v = vehicleRepository.findById(vehicleId)
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
			if (v.getBranch() == null || v.getBranch().getId() == null || v.getBranch().getId() != branchId) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Xe không thuộc chi nhánh đã chọn.");
			}
		}
		List<BookingSlot> templates = bookingSlotRepository.findByBranch_IdAndActiveTrueOrderBySlotTimeAsc(branchId);
		boolean isClosedDay = branchTemporarilyClosed || openingHoursProvider.isBranchClosedOnDate(branchId, date);
		return templates.stream()
				.map(slot -> {
					int max = slot.getMaxBookings() != null ? slot.getMaxBookings() : 0;
					boolean withinHours = openingHoursProvider.isWithinWorkingHours(branchId, date, slot.getSlotTime());
					boolean pastTime = date.isEqual(LocalDate.now()) && !slot.getSlotTime().isAfter(LocalTime.now());
					if (isClosedDay) {
						return AvailableSlotResponse.builder()
								.slotTime(slot.getSlotTime())
								.availableCount(0)
								.maxBookings(max)
								.bookable(false)
								.unavailableReason(REASON_BRANCH_CLOSED)
								.build();
					}
					if (!withinHours) {
						return AvailableSlotResponse.builder()
								.slotTime(slot.getSlotTime())
								.availableCount(0)
								.maxBookings(max)
								.bookable(false)
								.unavailableReason(REASON_OUTSIDE_WORKING_HOURS)
								.build();
					}
					if (pastTime) {
						return AvailableSlotResponse.builder()
								.slotTime(slot.getSlotTime())
								.availableCount(0)
								.maxBookings(max)
								.bookable(false)
								.unavailableReason(REASON_PAST_TIME)
								.build();
					}

					long taken = bookingRepository.countAtBranchSlot(branchId, date, slot.getSlotTime(),
							BookingSlotCounting.BRANCH_OCCUPIED_STATUSES);
					int branchAvailable = (int) Math.max(0, max - taken);
					if (vehicleId != null) {
						long vTaken = bookingRepository.countAtVehicleSlot(vehicleId, date, slot.getSlotTime(),
								BookingSlotCounting.VEHICLE_TRIPLE_OCCUPIED_STATUSES);
						int available = (branchAvailable <= 0 || vTaken > 0) ? 0 : 1;
						String reason = null;
						if (branchAvailable <= 0) {
							reason = REASON_FULL;
						}
						else if (vTaken > 0) {
							reason = REASON_VEHICLE_CONFLICT;
						}
						return AvailableSlotResponse.builder()
								.slotTime(slot.getSlotTime())
								.availableCount(available)
								.maxBookings(max)
								.bookable(available > 0)
								.unavailableReason(reason)
								.build();
					}

					int available = branchAvailable;
					return AvailableSlotResponse.builder()
							.slotTime(slot.getSlotTime())
							.availableCount(available)
							.maxBookings(max)
							.bookable(available > 0)
							.unavailableReason(available > 0 ? null : REASON_FULL)
							.build();
				})
				.toList();
	}
}
