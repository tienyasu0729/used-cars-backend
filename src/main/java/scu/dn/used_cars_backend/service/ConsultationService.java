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
			for (Long adminId : userRepository.findActiveAdminUserIds()) {
				inAppNotificationService.createNotification(adminId, "consultation", title, body, "/admin/consultations");
			}
			return;
		}
		int branchId = c.getVehicle().getBranch().getId();
		var ids = userRepository.findConsultationNotifyRecipientIdsAtBranch(branchId);
		var seen = new LinkedHashSet<Long>();
		for (Long uid : ids) {
			if (uid != null && seen.add(uid)) {
				inAppNotificationService.createNotification(uid, "consultation", title, body, "/staff/consultations");
			}
		}
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
		User actor = userRepository.findByIdAndDeletedFalse(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		c.setAssignedStaff(actor);
		c.setStatus("processing");
		consultationRepository.save(c);
		if (c.getCustomer() != null) {
			inAppNotificationService.createNotification(c.getCustomer().getId(), "consultation",
					"Phiếu tư vấn đang được xử lý",
					"Đội ngũ showroom đang xử lý yêu cầu của bạn.", "/notifications");
		}
	}

	@Transactional
	public void updateStatus(long actorUserId, String jwtRole, long consultationId, PatchConsultationStatusRequest body) {
		String st = body.getStatus().trim().toLowerCase(Locale.ROOT);
		if (!VALID_STATUS.contains(st)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status phải là pending, processing hoặc resolved.");
		}
		Consultation c = loadConsultationOrThrow(consultationId);
		assertCanAccess(c, actorUserId, jwtRole);
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
