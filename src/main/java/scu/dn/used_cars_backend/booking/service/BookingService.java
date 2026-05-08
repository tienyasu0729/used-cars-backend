package scu.dn.used_cars_backend.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.booking.BookingSlotCounting;
import scu.dn.used_cars_backend.booking.BranchOpeningHoursProvider;
import scu.dn.used_cars_backend.booking.dto.BookingResponse;
import scu.dn.used_cars_backend.booking.dto.BookingStatusHistoryItemDto;
import scu.dn.used_cars_backend.booking.dto.AssignBookingStaffRequest;
import scu.dn.used_cars_backend.booking.dto.CancelBookingRequest;
import scu.dn.used_cars_backend.booking.dto.ConfirmBookingRequest;
import scu.dn.used_cars_backend.booking.dto.CreateBookingRequest;
import scu.dn.used_cars_backend.booking.dto.CreateManagerBookingRequest;
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
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.entity.VehicleStatus;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.DepositRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.InAppNotificationService;
import scu.dn.used_cars_backend.service.ShowroomCustomerService;
import scu.dn.used_cars_backend.service.StaffService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BookingService {

	private static final int NOTIFICATION_BODY_MAX_LEN = 1000;
	private static final List<String> NO_SHOW_MANUAL_ALLOWED_STATUSES = List.of("Confirmed", "Rescheduled");
	private static final List<String> NO_SHOW_AUTO_SOURCE_STATUSES = List.of("Confirmed", "Rescheduled");

	private final BookingRepository bookingRepository;
	private final BookingSlotRepository bookingSlotRepository;
	private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
	private final VehicleRepository vehicleRepository;
	private final BranchRepository branchRepository;
	private final DepositRepository depositRepository;
	private final BranchOpeningHoursProvider openingHoursProvider;
	private final UserRepository userRepository;
	private final StaffService staffService;
	private final InAppNotificationService inAppNotificationService;
	private final ShowroomCustomerService showroomCustomerService;

	@Transactional(rollbackFor = Exception.class)
	public BookingResponse createBooking(CreateBookingRequest request, long customerId) {
		LocalDate bookingDate = parseDate(request.getBookingDate());
		LocalTime timeSlot = parseTime(request.getTimeSlot());
		return createBookingInternal(
				request.getVehicleId(),
				request.getBranchId(),
				bookingDate,
				timeSlot,
				trimToNull(request.getNote()),
				customerId,
				"AwaitingContract",
				customerId,
				null,
				false);
	}

	@Transactional(rollbackFor = Exception.class)
	public BookingResponse createManagerBooking(CreateManagerBookingRequest request, long actorUserId) {
		long customerId = showroomCustomerService.findOrCreate(request.getCustomer());
		LocalDate bookingDate = parseDate(request.getBookingDate());
		LocalTime timeSlot = parseTime(request.getTimeSlot());
		String type = trimToNull(request.getType());
		String historyNote = type == null ? "Tạo lịch từ showroom" : "Tạo lịch từ showroom (" + type + ")";
		return createBookingInternal(
				request.getVehicleId(),
				request.getBranchId(),
				bookingDate,
				timeSlot,
				trimToNull(request.getNote()),
				customerId,
				"Confirmed",
				actorUserId,
				historyNote,
				true);
	}

	@Transactional
	public BookingResponse activateBookingAfterContractSigned(long bookingId, long customerId) {
		Booking b = loadBookingWithDetails(bookingId);
		if (!b.getCustomerId().equals(customerId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Không có quyền thao tác lịch hẹn này.");
		}
		if (!"AwaitingContract".equals(b.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Lịch hẹn không ở trạng thái chờ ký hợp đồng.");
		}
		b.setStatus("Confirmed");
		bookingRepository.save(b);
		appendHistory(b, "AwaitingContract", "Pending", customerId, "Hợp đồng lái thử đã ký");
		Booking persisted = loadBookingWithDetails(bookingId);
		notifyBranchStaffNewBooking(persisted);
		return toResponse(persisted, false);
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
	public BookingResponse cancelBooking(long bookingId, long actorId, boolean staffActor, CancelBookingRequest request) {
		Booking b = loadBookingWithDetails(bookingId);
		if (!staffActor && !b.getCustomerId().equals(actorId)) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Kh??ng c?? quy???n h???y l???ch h???n n??y.");
		}
		String cur = b.getStatus();
		if (!List.of("AwaitingContract", "Pending", "Confirmed", "Rescheduled").contains(cur)) {
			throw new BusinessException(ErrorCode.BOOKING_CANNOT_CANCEL, "L???ch h???n n??y kh??ng th??? h???y.");
		}
		String reason = trimToNull(request != null ? request.getNote() : null);
		if (staffActor && reason == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Staff ph???i nh???p l?? do khi h???y l???ch h???n.");
		}
		String old = b.getStatus();
		b.setStatus("Cancelled");
		bookingRepository.save(b);
		appendHistory(b, old, "Cancelled", actorId, reason);
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
		validateBookingNotInPast(newDate, newTime);
		int branchId = b.getBranch().getId();

		if (!openingHoursProvider.isWithinWorkingHours(branchId, newDate, newTime)) {
			throw new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Khung giờ mới ngoài giờ làm việc.");
		}

		BookingSlot lockedSlot = bookingSlotRepository.findActiveForUpdate(branchId, newTime)
				.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Không tìm thấy khung giờ."));

		long taken = bookingRepository.countAtBranchSlotExcluding(branchId, newDate, newTime,
				BookingSlotCounting.BRANCH_OCCUPIED_STATUSES, b.getId());
		int max = lockedSlot.getMaxBookings() != null ? lockedSlot.getMaxBookings() : 0;
		if (taken >= max) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Khung giờ mới đã đầy.");
		}

		long vehicleTaken = bookingRepository.countAtVehicleSlotExcluding(b.getVehicle().getId(), newDate, newTime,
				BookingSlotCounting.VEHICLE_TRIPLE_OCCUPIED_STATUSES, b.getId());
		if (vehicleTaken > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_SLOT_TAKEN,
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
	public BookingResponse assignStaff(long bookingId, AssignBookingStaffRequest request, int branchIdQuery,
			long actorUserId, boolean actorIsAdmin) {
		Long newStaffId = request != null ? request.getStaffId() : null;
		int resolvedBranch = staffService.resolveBranchIdForAdminOrBranchStaff(
				actorIsAdmin ? Long.valueOf(branchIdQuery) : null, actorUserId, actorIsAdmin);

		Booking b = loadBookingWithDetails(bookingId);
		if (b.getBranch().getId() != resolvedBranch) {
			throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "Lịch hẹn không thuộc chi nhánh bạn quản lý.");
		}
		String st = b.getStatus();
		if (!List.of("Pending", "Confirmed", "Rescheduled").contains(st)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"Không thể đổi nhân viên phụ trách ở trạng thái hiện tại.");
		}
		if (newStaffId != null) {
			staffService.assertActiveStaffLinkedToBranch(newStaffId, resolvedBranch);
		}
		b.setStaffId(newStaffId);
		bookingRepository.save(b);
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

	@Transactional
	public BookingResponse completeTestDrive(long bookingId, long staffId) {
		return completeBooking(bookingId, staffId);
	}

	@Transactional
	public BookingResponse markNoShow(long bookingId, long actorUserId, boolean actorIsAdmin) {
		Booking b = loadBookingWithDetails(bookingId);
		if (!actorIsAdmin) {
			int actorBranchId = staffService.resolveBranchIdForAdminOrBranchStaff(null, actorUserId, false);
			if (b.getBranch().getId() != actorBranchId) {
				throw new BusinessException(ErrorCode.BOOKING_ACCESS_DENIED, "L???ch h???n kh??ng thu???c chi nh??nh b???n qu???n l??.");
			}
		}
		String old = b.getStatus();
		if (!NO_SHOW_MANUAL_ALLOWED_STATUSES.contains(old)) {
			throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
					"Ch??? c?? th??? ????nh d???u v???ng m???t cho l???ch ???? x??c nh???n ho???c ???? ?????i l???ch.");
		}
		b.setStatus("Cancelled");
		bookingRepository.save(b);
		appendHistory(b, old, "Cancelled", actorUserId, "Kh??ch h??ng kh??ng t???i.");
		return toResponse(b, false);
	}

	@Transactional
	public int autoMarkOverdueBookingsAsNoShow(LocalDateTime now) {
		if (now == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "now kh??ng ???????c ????? tr???ng.");
		}
		LocalDateTime threshold = now.minusMinutes(30);
		List<Long> ids = bookingRepository.findIdsByStatusInAndStartedBeforeThreshold(
				threshold.toLocalDate(), threshold.toLocalTime(), NO_SHOW_AUTO_SOURCE_STATUSES);
		int updated = 0;
		for (Long id : ids) {
			Booking b = loadBookingWithDetails(id);
			String current = b.getStatus();
			if (!NO_SHOW_AUTO_SOURCE_STATUSES.contains(current)) {
				continue;
			}
			LocalDateTime bookingAt = LocalDateTime.of(b.getBookingDate(), b.getTimeSlot());
			if (bookingAt.isAfter(threshold)) {
				continue;
			}
			b.setStatus("Cancelled");
			bookingRepository.save(b);
			appendHistory(b, current, "Cancelled", null, "[AUTO] Kh??ch h??ng kh??ng t???i sau 30 ph??t.");
			updated++;
		}
		return updated;
	}

	private BookingResponse createBookingInternal(
			Long vehicleId,
			Integer branchId,
			LocalDate bookingDate,
			LocalTime timeSlot,
			String note,
			long customerId,
			String initialStatus,
			Long historyChangedBy,
			String historyNote,
			boolean notifyAfterCreate) {
		Branch branch = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));

		Vehicle vehicle = vehicleRepository.findAvailableForBooking(vehicleId, VehicleStatus.AVAILABLE.getDbValue())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Xe này hiện không thể đặt lịch."));
		if (depositRepository.countByVehicleIdAndStatusIn(vehicle.getId(),
				List.of("Pending", "Confirmed", "AwaitingPayment")) > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE,
					"Xe đang có cọc hoặc thanh toán đang xử lý — không đặt lịch lái thử.");
		}
		if (vehicle.getBranch() == null || vehicle.getBranch().getId() == null || vehicle.getBranch().getId() != branchId) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Xe không thuộc chi nhánh đã chọn.");
		}

		validateBookingNotInPast(bookingDate, timeSlot);

		if (!openingHoursProvider.isWithinWorkingHours(branchId, bookingDate, timeSlot)) {
			throw new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Khung giờ ngoài giờ làm việc.");
		}

		BookingSlot lockedSlot = bookingSlotRepository.findActiveForUpdate(branchId, timeSlot)
				.orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND, "Không tìm thấy khung giờ."));

		long taken = bookingRepository.countAtBranchSlot(branchId, bookingDate, timeSlot,
				BookingSlotCounting.BRANCH_OCCUPIED_STATUSES);
		int max = lockedSlot.getMaxBookings() != null ? lockedSlot.getMaxBookings() : 0;
		if (taken >= max) {
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Giờ này đã đầy, vui lòng chọn giờ khác.");
		}

		long vehicleTaken = bookingRepository.countAtVehicleSlot(vehicle.getId(), bookingDate, timeSlot,
				BookingSlotCounting.VEHICLE_TRIPLE_OCCUPIED_STATUSES);
		if (vehicleTaken > 0) {
			throw new BusinessException(ErrorCode.VEHICLE_SLOT_TAKEN,
					"Xe này đã có lịch hẹn trong khung giờ này. Vui lòng chọn giờ khác.");
		}

		Booking booking = new Booking();
		booking.setCustomerId(customerId);
		booking.setVehicle(vehicle);
		booking.setBranch(branch);
		booking.setBookingDate(bookingDate);
		booking.setTimeSlot(timeSlot);
		booking.setNote(note);
		booking.setStatus(initialStatus);
		booking.setStaffId(resolveAutoAssignedSalesStaffId(branchId));

		try {
			booking = bookingRepository.saveAndFlush(booking);
		}
		catch (DataIntegrityViolationException ex) {
			String cause = ex.getMostSpecificCause().getMessage();
			if (cause != null && cause.contains("UQ_Bookings_VehicleSlot")) {
				throw new BusinessException(ErrorCode.VEHICLE_SLOT_TAKEN,
						"Xe này đã có lịch tại khung giờ này. Vui lòng chọn giờ khác.");
			}
			throw new BusinessException(ErrorCode.SLOT_FULLY_BOOKED, "Giờ này đã đầy, vui lòng chọn giờ khác.");
		}

		appendHistory(booking, null, initialStatus, historyChangedBy, historyNote);
		Booking persisted = loadBookingWithDetails(booking.getId());
		if (notifyAfterCreate) {
			notifyBranchStaffNewBooking(persisted);
		}
		return toResponse(persisted, false);
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

	private void validateBookingNotInPast(LocalDate bookingDate, LocalTime timeSlot) {
		LocalDate today = LocalDate.now();
		if (bookingDate.isBefore(today)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Kh??ng th??? ?????t l???ch trong qu?? kh???.");
		}
		if (bookingDate.isEqual(today) && !timeSlot.isAfter(LocalTime.now())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Khung gi??? ???? qua, vui l??ng ch???n gi??? kh??c.");
		}
	}

	private Long resolveAutoAssignedSalesStaffId(int branchId) {
		List<User> salesStaff = userRepository.findActiveSalesStaffUsersByBranchId(branchId);
		if (salesStaff.isEmpty()) {
			throw new BusinessException(ErrorCode.STAFF_NOT_FOUND, "Chi nh??nh ch??a c?? sales staff ??ang ho???t ?????ng ????? nh???n l???ch h???n.");
		}
		long assignedCount = bookingRepository.countAssignedBookingsAtBranch(branchId);
		int index = (int) (assignedCount % salesStaff.size());
		return salesStaff.get(index).getId();
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
		String staffName = null;
		if (b.getStaffId() != null) {
			staffName = userRepository.findById(b.getStaffId())
					.map(User::getName)
					.filter(n -> n != null && !n.isBlank())
					.orElse(null);
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
				.staffName(staffName)
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

	private void notifyBranchStaffNewBooking(Booking b) {
		int branchId = b.getBranch().getId();
		LinkedHashSet<Long> recipientIds = new LinkedHashSet<>(userRepository.findConsultationNotifyRecipientIdsAtBranch(branchId));
		branchRepository.findActiveByIdWithManager(branchId)
				.map(Branch::getManager)
				.filter(Objects::nonNull)
				.map(User::getId)
				.ifPresent(recipientIds::add);

		String custLabel = "Khách";
		if (b.getCustomer() != null && b.getCustomer().getName() != null && !b.getCustomer().getName().isBlank()) {
			custLabel = b.getCustomer().getName();
		}
		String branchName = b.getBranch().getName() != null ? b.getBranch().getName() : ("CN #" + branchId);
		String vTitle = b.getVehicle() != null && b.getVehicle().getTitle() != null ? b.getVehicle().getTitle() : "xe";
		String title = "Lịch hẹn lái thử mới";
		String body = custLabel + " đặt lịch " + b.getBookingDate() + " " + b.getTimeSlot() + " — " + vTitle + " ("
				+ branchName + ")";
		if (body.length() > NOTIFICATION_BODY_MAX_LEN) {
			body = body.substring(0, NOTIFICATION_BODY_MAX_LEN);
		}
		for (Long uid : recipientIds) {
			if (uid == null) {
				continue;
			}
			User recipient = userRepository.findActiveByIdWithRoles(uid).orElse(null);
			if (recipient == null) {
				continue;
			}
			boolean isManager = recipient.getUserRoles().stream()
					.anyMatch(ur -> "BranchManager".equals(ur.getRole().getName()));
			String link = isManager ? "/manager/appointments" : "/staff/bookings";
			inAppNotificationService.createNotification(uid, "Booking", title, body, link);
		}
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
