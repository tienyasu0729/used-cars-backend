package scu.dn.used_cars_backend.service;

// Service xử lý phiếu tư vấn — tạo công khai, danh sách & cập nhật cho staff/manager/admin (Sprint 9).
// Fan-out thông báo: có xe → nhân sự SalesStaff/BranchManager có StaffAssignment active tại nhánh xe;
// không xe → chỉ Admin.

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.consultation.ConsultationListItemDto;
import scu.dn.used_cars_backend.dto.consultation.CreateConsultationRequest;
import scu.dn.used_cars_backend.dto.consultation.CreateConsultationResponse;
import scu.dn.used_cars_backend.dto.consultation.PatchConsultationStatusRequest;
import scu.dn.used_cars_backend.entity.Consultation;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.ConsultationRepository;
import scu.dn.used_cars_backend.repository.ConsultationSpecs;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConsultationService {

	private static final String ROLE_ADMIN = "ADMIN";

	private static final Set<String> VALID_STATUS = Set.of("pending", "processing", "resolved");
	private static final Set<String> VALID_PRIORITY = Set.of("low", "medium", "high");

	/** Cột body thông báo in-app tối đa 1000 ký tự (entity Notifications.body). */
	private static final int NOTIFICATION_BODY_MAX_LEN = 1000;

	private final ConsultationRepository consultationRepository;
	private final VehicleRepository vehicleRepository;
	private final UserRepository userRepository;
	private final StaffService staffService;
	private final InAppNotificationService inAppNotificationService;

	@Transactional
	public CreateConsultationResponse create(Long optionalCustomerUserId, CreateConsultationRequest req) {
		String priority = normalizePriority(req.getPriority());
		String msg = req.getMessage().trim();
		if (msg.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Nội dung tin nhắn không được để trống.");
		}
		Consultation c = new Consultation();
		c.setCustomerName(req.getCustomerName().trim());
		c.setCustomerPhone(req.getCustomerPhone().trim());
		c.setMessage(msg);
		c.setPriority(priority);
		c.setStatus("pending");
		if (optionalCustomerUserId != null) {
			User u = userRepository.findByIdAndDeletedFalse(optionalCustomerUserId)
					.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
			c.setCustomer(u);
		}
		if (req.getVehicleId() != null) {
			Vehicle v = vehicleRepository.findById(req.getVehicleId())
					.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
			c.setVehicle(v);
		}
		c = consultationRepository.save(c);
		sendNewConsultationNotifications(c);
		return new CreateConsultationResponse(c.getId(), true);
	}

	private void sendNewConsultationNotifications(Consultation c) {
		String title = "Phiếu tư vấn mới";
		String body = "Có phiếu tư vấn mới từ " + c.getCustomerName();
		if (c.getVehicle() == null) {
			// Phiếu không gắn xe → chỉ gửi cho Admin
			for (Long adminId : userRepository.findActiveAdminUserIds()) {
				inAppNotificationService.createNotification(adminId, "consultation", title, body, "/admin/consultations");
			}
			return;
		}
		int branchId = c.getVehicle().getBranch().getId();
		var ids = userRepository.findConsultationNotifyRecipientIdsAtBranch(branchId);
		var seen = new LinkedHashSet<Long>();
		for (Long uid : ids) {
			if (uid == null || !seen.add(uid)) continue;
			// B1: Lấy thông tin user để biết role (Manager hay Staff)
			User recipient = userRepository.findActiveByIdWithRoles(uid).orElse(null);
			if (recipient == null) continue;
			// B2: Phân biệt link theo role — Manager dùng /manager/, Staff dùng /staff/
			boolean isManager = recipient.getUserRoles().stream()
					.anyMatch(ur -> "BranchManager".equals(ur.getRole().getName()));
			String link = isManager ? "/manager/consultations" : "/staff/consultations";
			inAppNotificationService.createNotification(uid, "consultation", title, body, link);
		}
	}

	/** Khách đăng nhập xem phiếu của mình (thông báo inbox / modal chi tiết). */
	@Transactional(readOnly = true)
	public ConsultationListItemDto getForCustomer(long customerUserId, long consultationId) {
		Consultation c = loadConsultationOrThrow(consultationId);
		if (c.getCustomer() == null || !c.getCustomer().getId().equals(customerUserId)) {
			throw new BusinessException(ErrorCode.CONSULTATION_ACCESS_DENIED, "Bạn không xem được phiếu tư vấn này.");
		}
		return toListDto(c);
	}

	@Transactional(readOnly = true)
	public Page<ConsultationListItemDto> list(long actorUserId, String jwtRole, String status, String priority,
			Boolean hasVehicle, int page, int size) {
		Specification<Consultation> spec = Specification.allOf(ConsultationSpecs.statusEqualsIgnoreCase(status),
				ConsultationSpecs.priorityEqualsIgnoreCase(priority));
		if (ROLE_ADMIN.equals(jwtRole)) {
			spec = Specification.allOf(spec, ConsultationSpecs.hasVehicle(hasVehicle));
		}
		else {
			int branchId = staffService.getManagerBranchId(actorUserId);
			spec = Specification.allOf(spec, ConsultationSpecs.vehicleBranchId(branchId));
		}
		Pageable pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return consultationRepository.findAll(spec, pr).map(this::toListDto);
	}

	private ConsultationListItemDto toListDto(Consultation c) {
		Long vid = c.getVehicle() != null ? c.getVehicle().getId() : null;
		String vtitle = c.getVehicle() != null ? c.getVehicle().getTitle() : null;
		Long custId = c.getCustomer() != null ? c.getCustomer().getId() : null;
		Long asgId = c.getAssignedStaff() != null ? c.getAssignedStaff().getId() : null;
		String asgName = c.getAssignedStaff() != null ? c.getAssignedStaff().getName() : null;
		return ConsultationListItemDto.builder()
				.id(c.getId())
				.customerId(custId)
				.customerName(c.getCustomerName())
				.customerPhone(c.getCustomerPhone())
				.vehicleId(vid)
				.vehicleTitle(vtitle)
				.message(c.getMessage())
				.status(c.getStatus())
				.priority(c.getPriority())
				.assignedStaffId(asgId)
				.assignedStaffName(asgName)
				.createdAt(c.getCreatedAt())
				.updatedAt(c.getUpdatedAt())
				.build();
	}

	@Transactional
	public void respond(long actorUserId, String jwtRole, long consultationId) {
		Consultation c = loadConsultationOrThrow(consultationId);
		assertCanAccess(c, actorUserId, jwtRole);
		// B1: Chỉ phiếu đang chờ (pending) mới được tiếp nhận — tránh staff B ghi đè staff A
		if (!"pending".equals(c.getStatus())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ phiếu đang chờ xử lý mới được tiếp nhận.");
		}
		User actor = userRepository.findByIdAndDeletedFalse(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		c.setAssignedStaff(actor);
		c.setStatus("processing");
		consultationRepository.save(c);
		if (c.getCustomer() != null) {
			// link nội bộ: FE đọc consultation-ref:{id} để gọi GET /consultations/{id}/mine — không điều hướng trang.
			inAppNotificationService.createNotification(c.getCustomer().getId(), "consultation",
					"Phiếu tư vấn đang được xử lý",
					buildCustomerConsultationProcessingBody(c),
					"consultation-ref:" + c.getId());
		}
	}

	/** Nội dung popup khách xem: xe (nếu có) + tin nhắn đã gửi + mã phiếu — cắt gọn nếu vượt DB. */
	private static String buildCustomerConsultationProcessingBody(Consultation c) {
		String vehicleLine;
		if (c.getVehicle() != null) {
			String t = c.getVehicle().getTitle();
			vehicleLine = (t != null && !t.isBlank()) ? t.trim() : "Xe (chưa có tiêu đề)";
		} else {
			vehicleLine = "Không gắn xe cụ thể";
		}
		String msg = c.getMessage() != null ? c.getMessage().trim() : "";
		if (msg.isEmpty()) {
			msg = "—";
		}
		String prefix = "Đội ngũ showroom đang xử lý yêu cầu của bạn.\n\nXe quan tâm: " + vehicleLine
				+ "\n\nNội dung bạn yêu cầu tư vấn:\n";
		String suffix = "\n\nMã phiếu: #" + c.getId();
		String full = prefix + msg + suffix;
		if (full.length() <= NOTIFICATION_BODY_MAX_LEN) {
			return full;
		}
		int budget = NOTIFICATION_BODY_MAX_LEN - prefix.length() - suffix.length() - 3;
		if (budget < 24) {
			return full.substring(0, NOTIFICATION_BODY_MAX_LEN - 3) + "...";
		}
		String clipped = msg.length() <= budget ? msg : msg.substring(0, budget) + "...";
		return prefix + clipped + suffix;
	}

	@Transactional
	public void updateStatus(long actorUserId, String jwtRole, long consultationId, PatchConsultationStatusRequest body) {
		String st = body.getStatus().trim().toLowerCase(Locale.ROOT);
		if (!VALID_STATUS.contains(st)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status phải là pending, processing hoặc resolved.");
		}
		Consultation c = loadConsultationOrThrow(consultationId);
		assertCanAccess(c, actorUserId, jwtRole);
		// B1: Kiểm tra state machine — chỉ cho phép chuyển chiều hợp lệ
		String current = c.getStatus();
		boolean validTransition = switch (current) {
			case "pending"    -> Set.of("processing").contains(st);
			case "processing" -> Set.of("resolved").contains(st);
			case "resolved"   -> ROLE_ADMIN.equals(jwtRole); // Chỉ Admin mới có thể mở lại
			default           -> false;
		};
		if (!validTransition) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Không thể chuyển từ '" + current + "' sang '" + st + "'.");
		}
		c.setStatus(st);
		consultationRepository.save(c);
	}

	private Consultation loadConsultationOrThrow(long id) {
		return consultationRepository.findWithDetailsById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND, "Không tìm thấy phiếu tư vấn."));
	}

	private void assertCanAccess(Consultation c, long actorUserId, String jwtRole) {
		if (ROLE_ADMIN.equals(jwtRole)) {
			return;
		}
		if (c.getVehicle() == null) {
			throw new BusinessException(ErrorCode.CONSULTATION_ACCESS_DENIED, "Phiếu không gắn xe chỉ Admin xử lý.");
		}
		int branchId = staffService.getManagerBranchId(actorUserId);
		if (c.getVehicle().getBranch().getId() != branchId) {
			throw new BusinessException(ErrorCode.CONSULTATION_ACCESS_DENIED, "Phiếu không thuộc chi nhánh của bạn.");
		}
	}

	private static String normalizePriority(String raw) {
		if (raw == null || raw.isBlank()) {
			return "medium";
		}
		String p = raw.trim().toLowerCase(Locale.ROOT);
		if (!VALID_PRIORITY.contains(p)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "priority phải là low, medium hoặc high.");
		}
		return p;
	}
}
