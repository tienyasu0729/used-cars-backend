package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.BranchOpeningHoursProvider;
import scu.dn.used_cars_backend.booking.dto.BookingResponse;
import scu.dn.used_cars_backend.booking.dto.BookingStatusHistoryItemDto;
import scu.dn.used_cars_backend.booking.dto.ConfirmBookingRequest;
import scu.dn.used_cars_backend.booking.dto.CreateBookingRequest;
import scu.dn.used_cars_backend.booking.dto.RescheduleRequest;
import scu.dn.used_cars_backend.booking.dto.ScheduleGroupResponse;
import scu.dn.used_cars_backend.booking.entity.Booking;
import scu.dn.used_cars_backend.booking.entity.BookingSlot;
import scu.dn.used_cars_backend.booking.entity.BookingStatusHistory;
import scu.dn.used_cars_backend.booking.repository.BookingRepository;
import scu.dn.used_cars_backend.booking.repository.BookingSlotRepository;
import scu.dn.used_cars_backend.booking.repository.BookingStatusHistoryRepository;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingService {

	private static final List<String> SLOT_COUNT_STATUSES = List.of("Pending", "Confirmed");

	private final BookingRepository bookingRepository;
	private final BookingSlotRepository bookingSlotRepository;
	private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
	private final VehicleRepository vehicleRepository;
	private final BranchRepository branchRepository;
	private final DepositRepository depositRepository;
	private final BranchOpeningHoursProvider openingHoursProvider;

	@Transactional(rollbackFor = Exception.class)
	public BookingResponse createBooking(CreateBookingRequest request, long customerId) {
		LocalDate bookingDate = parseDate(request.getBookingDate());
		LocalTime timeSlot = parseTime(request.getTimeSlot());
		int branchId = request.getBranchId();

		Branch branch = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));

		Vehicle vehicle = vehicleRepository.findAvailableForBooking(request.getVehicleId(),
						VehicleStatus.AVAILABLE.getDbValue())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe này hiện không thể đặt lịch."));
		if (depositRepository.countByVehicleIdAndStatusIn(vehicle.getId(),
				List.of("Pending", "Confirmed", "AwaitingPayment")) > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE,
					"Xe đang có cọc hoặc thanh toán đang xử lý — không đặt lịch lái thử.");
		}
		if (vehicle.getBranch() == null || vehicle.getBranch().getId() == null
				|| vehicle.getBranch().getId() != branchId) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Xe không thuộc chi nhánh đã chọn.");
		}

		if (!openingHoursProvider.isWithinWorkingHours(branchId, bookingDate, timeSlot)) {
			throw new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Khung giờ ngoài giờ làm việc.");
		}

		BookingSlot lockedSlot = bookingSlotRepository.findActiveForUpdate(branchId, timeSlot)
				.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Không tìm thấy khung giờ."));

		long taken = bookingRepository.countAtBranchSlot(branchId, bookingDate, timeSlot, SLOT_COUNT_STATUSES);
		int max = lockedSlot.getMaxBookings() != null ? lockedSlot.getMaxBookings() : 0;
		if (taken >= max) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Giờ này đã đầy, vui lòng chọn giờ khác.");
		}

		long vehicleTaken = bookingRepository.countAtVehicleSlot(vehicle.getId(), bookingDate, timeSlot,
				SLOT_COUNT_STATUSES);
		if (vehicleTaken > 0) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED,
					"Xe này đã có lịch hẹn trong khung giờ này. Vui lòng chọn giờ khác.");
		}

		Booking booking = new Booking();
		booking.setCustomerId(customerId);
		booking.setVehicle(vehicle);
		booking.setBranch(branch);
		booking.setBookingDate(bookingDate);
		booking.setTimeSlot(timeSlot);
		booking.setNote(trimToNull(request.getNote()));
		booking.setStatus("Pending");

		try {
			booking = bookingRepository.saveAndFlush(booking);
		}
		catch (DataIntegrityViolationException ex) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Giờ này đã đầy, vui lòng chọn giờ khác.");
		}

		appendHistory(booking, null, "Pending", customerId, null);
		return toResponse(booking.getId(), false);
	}

	@Transactional(readOnly = true)
	public Page<BookingResponse> listMyBookings(long customerId, String status, Pageable pageable) {
		String st = normalizeStatusFilter(status);
		return bookingRepository.findByCustomerIdAndOptionalStatus(customerId, st, pageable).map(b -> toResponse(b, false));
	}

	@Transactional(readOnly = true)
	public BookingResponse getBookingForCustomer(long bookingId, long customerId) {
		Booking b = loadBookingWithDetails(bookingId);
		if (!b.getCustomerId().equals(customerId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền xem lịch hẹn này.");
		}
		return toResponse(b, true);
	}

	@Transactional
	public BookingResponse cancelBooking(long bookingId, long actorId, boolean staffActor) {
		Booking b = loadBookingWithDetails(bookingId);
		if (!staffActor && !b.getCustomerId().equals(actorId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền hủy lịch hẹn này.");
		}
		String cur = b.getStatus();
		if (!List.of("Pending", "Confirmed", "Rescheduled").contains(cur)) {
			throw new BusinessException(ErrorCode.BOOKING_CANNOT_CANCEL, "Lịch hẹn này không thể hủy.");
		}
		String old = b.getStatus();
		b.setStatus("Cancelled");
		bookingRepository.save(b);
		appendHistory(b, old, "Cancelled", actorId, null);
		return toResponse(b, false);
	}

	@Transactional(readOnly = true)
	public Page<BookingResponse> listStaffBookings(int branchId, String status, Pageable pageable) {
		if (branchRepository.findByIdAndDeletedFalse(branchId).isEmpty()) {
			throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh.");
		}
		String st = normalizeStatusFilter(status);
		return bookingRepository.findByBranchIdAndOptionalStatus(branchId, st, pageable).map(b -> toResponse(b, false));
	}

	@Transactional(readOnly = true)
	public List<ScheduleGroupResponse> getStaffSchedule(int branchId, LocalDate date) {
		if (branchRepository.findByIdAndDeletedFalse(branchId).isEmpty()) {
			throw new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh.");
		}
		List<Booking> rows = bookingRepository.findScheduleForBranchAndDate(branchId, date);
		Map<LocalTime, List<BookingResponse>> map = new LinkedHashMap<>();
		for (Booking b : rows) {
			map.computeIfAbsent(b.getTimeSlot(), k -> new ArrayList<>()).add(toResponse(b, false));
		}
		List<ScheduleGroupResponse> out = new ArrayList<>();
		for (Map.Entry<LocalTime, List<BookingResponse>> e : map.entrySet()) {
			out.add(ScheduleGroupResponse.builder().timeSlot(e.getKey()).bookings(e.getValue()).build());
		}
		return out;
	}

	@Transactional
	public BookingResponse confirmBooking(long bookingId, long staffId, ConfirmBookingRequest req) {
		Booking b = loadBookingWithDetails(bookingId);
		String old = b.getStatus();
		if (!List.of("Pending", "Rescheduled").contains(old)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Chỉ có thể xác nhận lịch đang chờ hoặc đã đổi lịch.");
		}
		b.setStatus("Confirmed");
		b.setStaffId(staffId);
		bookingRepository.save(b);
		appendHistory(b, old, "Confirmed", staffId, req != null ? trimToNull(req.getNote()) : null);
		return toResponse(b, false);
	}

	@Transactional
	public BookingResponse rescheduleBooking(long bookingId, long staffId, RescheduleRequest request) {
		Booking b = loadBookingWithDetails(bookingId);
		String oldStatus = b.getStatus();
		if (!List.of("Pending", "Confirmed", "Rescheduled").contains(oldStatus)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Không thể đổi lịch ở trạng thái hiện tại.");
		}
		LocalDate newDate = parseDate(request.getNewBookingDate());
		LocalTime newTime = parseTime(request.getNewTimeSlot());
		int branchId = b.getBranch().getId();

		if (!openingHoursProvider.isWithinWorkingHours(branchId, newDate, newTime)) {
			throw new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Khung giờ mới ngoài giờ làm việc.");
		}

		BookingSlot lockedSlot = bookingSlotRepository.findActiveForUpdate(branchId, newTime)
				.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Không tìm thấy khung giờ."));

		long taken = bookingRepository.countAtBranchSlotExcluding(branchId, newDate, newTime, SLOT_COUNT_STATUSES,
				b.getId());
		int max = lockedSlot.getMaxBookings() != null ? lockedSlot.getMaxBookings() : 0;
		if (taken >= max) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Khung giờ mới đã đầy.");
		}

		long vehicleTaken = bookingRepository.countAtVehicleSlotExcluding(b.getVehicle().getId(), newDate, newTime,
				SLOT_COUNT_STATUSES, b.getId());
		if (vehicleTaken > 0) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED,
					"Xe đã có lịch hẹn khác trong khung giờ mới này.");
		}

		String old = b.getStatus();
		b.setBookingDate(newDate);
		b.setTimeSlot(newTime);
		b.setStatus("Rescheduled");
		try {
			bookingRepository.saveAndFlush(b);
		}
		catch (DataIntegrityViolationException ex) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Không thể đổi lịch do trùng lịch xe.");
		}
		appendHistory(b, old, "Rescheduled", staffId, trimToNull(request.getNote()));
		return toResponse(b, false);
	}

	@Transactional
	public BookingResponse completeBooking(long bookingId, long staffId) {
		Booking b = loadBookingWithDetails(bookingId);
		String old = b.getStatus();
		if (!"Confirmed".equals(old)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Chỉ hoàn thành lịch đã xác nhận.");
		}
		b.setStatus("Completed");
		bookingRepository.save(b);
		appendHistory(b, old, "Completed", staffId, null);
		return toResponse(b, false);
	}

	private Booking loadBookingWithDetails(long id) {
		return bookingRepository.findWithDetailsById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy lịch hẹn."));
	}

	private void appendHistory(Booking booking, String oldStatus, String newStatus, Long changedBy, String note) {
		BookingStatusHistory h = new BookingStatusHistory();
		h.setBooking(booking);
		h.setOldStatus(oldStatus);
		h.setNewStatus(newStatus);
		h.setChangedBy(changedBy);
		h.setNote(note);
		bookingStatusHistoryRepository.save(h);
	}

	private BookingResponse toResponse(Booking b, boolean withHistory) {
		Vehicle v = b.getVehicle();
		String title = v.getTitle();
		List<BookingStatusHistoryItemDto> hist = null;
		if (withHistory) {
			hist = bookingStatusHistoryRepository.findByBooking_IdOrderByChangedAtAsc(b.getId()).stream()
					.map(this::toHistDto)
					.toList();
		}
		String custName = null;
		String custPhone = null;
		if (b.getCustomer() != null) {
			custName = b.getCustomer().getName();
			custPhone = b.getCustomer().getPhone();
		}
		return BookingResponse.builder()
				.id(b.getId())
				.customerId(b.getCustomerId())
				.customerName(custName)
				.customerPhone(custPhone)
				.vehicleId(v.getId())
				.vehicleTitle(title)
				.vehicleListingId(v.getListingId())
				.branchId(b.getBranch().getId())
				.branchName(b.getBranch().getName())
				.bookingDate(b.getBookingDate())
				.timeSlot(b.getTimeSlot())
				.staffId(b.getStaffId())
				.status(b.getStatus())
				.note(b.getNote())
				.createdAt(b.getCreatedAt())
				.statusHistory(hist)
				.build();
	}

	private BookingResponse toResponse(long bookingId, boolean withHistory) {
		Booking b = loadBookingWithDetails(bookingId);
		return toResponse(b, withHistory);
	}

	private BookingStatusHistoryItemDto toHistDto(BookingStatusHistory h) {
		return BookingStatusHistoryItemDto.builder()
				.oldStatus(h.getOldStatus())
				.newStatus(h.getNewStatus())
				.changedBy(h.getChangedBy())
				.note(h.getNote())
				.changedAt(h.getChangedAt())
				.build();
	}

	private static String normalizeStatusFilter(String status) {
		if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
			return null;
		}
		return status.trim();
	}

	private static LocalDate parseDate(String raw) {
		try {
			return LocalDate.parse(raw.trim());
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Định dạng ngày không hợp lệ.");
		}
	}

	private static LocalTime parseTime(String raw) {
		try {
			return LocalTime.parse(raw.trim());
		}
		catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Định dạng giờ không hợp lệ.");
		}
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
