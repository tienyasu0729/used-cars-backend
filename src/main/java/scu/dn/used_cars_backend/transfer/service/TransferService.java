package scu.dn.used_cars_backend.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.Vehicle;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;
import scu.dn.used_cars_backend.service.VehicleService;
import scu.dn.used_cars_backend.transfer.dto.CompleteTransferRequestDto;
import scu.dn.used_cars_backend.transfer.dto.CreateTransferRequestDto;
import scu.dn.used_cars_backend.transfer.dto.TransferActionRequestDto;
import scu.dn.used_cars_backend.transfer.dto.TransferApprovalHistoryItemDto;
import scu.dn.used_cars_backend.transfer.dto.TransferResponseDto;
import scu.dn.used_cars_backend.transfer.entity.TransferApprovalHistory;
import scu.dn.used_cars_backend.transfer.entity.TransferRequest;
import scu.dn.used_cars_backend.transfer.repository.TransferApprovalHistoryRepository;
import scu.dn.used_cars_backend.transfer.repository.TransferRequestRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransferService {

	private static final Set<String> ACTIVE_TRANSFER_STATUSES = Set.of("Pending", "Approved");
	private static final Set<String> ALLOWED_STATUSES = Set.of("Pending", "Approved", "Rejected", "Completed");

	private final TransferRequestRepository transferRequestRepository;
	private final TransferApprovalHistoryRepository transferApprovalHistoryRepository;
	private final VehicleRepository vehicleRepository;
	private final BranchRepository branchRepository;
	private final UserRepository userRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final VehicleService vehicleService;

	@Transactional
	public TransferResponseDto create(CreateTransferRequestDto body, long userId, boolean isAdmin) {
		if (isAdmin) {
			throw new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED, "Chỉ BranchManager được tạo yêu cầu điều chuyển.");
		}
		// B1: Chi nhánh quản lý từ StaffAssignments hoặc Branches.manager_id
		int managerBranchId = requireManagerBranchId(userId);

		// B2: Load xe + kiểm tra tồn tại, chưa xóa
		Vehicle vehicle = vehicleRepository.findManagedDetailById(body.getVehicleId())
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe."));
		if (vehicle.isDeleted()) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND, "Không tìm thấy xe.");
		}
		// B3: Xe thuộc branch của manager
		if (!Objects.equals(vehicle.getBranch().getId(), managerBranchId)) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_IN_BRANCH, "Xe không thuộc chi nhánh của bạn.");
		}
		// B4: Trạng thái Available
		if (!"Available".equals(vehicle.getStatus())) {
			throw new BusinessException(ErrorCode.VEHICLE_NOT_AVAILABLE, "Chỉ xe Available mới tạo điều chuyển.");
		}
		// B5: Chi nhánh đích hợp lệ, khác nguồn
		int toBranchId = body.getToBranchId();
		if (managerBranchId == toBranchId) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chi nhánh đích phải khác chi nhánh nguồn.");
		}
		branchRepository.findByIdAndDeletedFalse(toBranchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh đích."));
		// B6: Không có Pending/Approved cho cùng xe
		if (transferRequestRepository.existsByVehicle_IdAndStatusIn(vehicle.getId(), ACTIVE_TRANSFER_STATUSES)) {
			throw new BusinessException(ErrorCode.TRANSFER_ALREADY_EXISTS, "Xe đang có yêu cầu điều chuyển chưa kết thúc.");
		}

		TransferRequest tr = new TransferRequest();
		tr.setVehicle(vehicle);
		tr.setFromBranchId(managerBranchId);
		tr.setToBranchId(toBranchId);
		tr.setRequestedBy(userId);
		tr.setStatus("Pending");
		tr.setReason(trimToNull(body.getReason()));

		TransferRequest saved = transferRequestRepository.save(tr);
		return toResponse(saved, false);
	}

	@Transactional(readOnly = true)
	public Page<TransferResponseDto> list(String status, Pageable pageable, long userId, boolean isAdmin) {
		if (status != null && !status.isBlank() && !ALLOWED_STATUSES.contains(status.trim())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trạng thái lọc không hợp lệ.");
		}
		String st = status != null && !status.isBlank() ? status.trim() : null;
		Integer branchScope = isAdmin ? null : requireManagerBranchId(userId);
		Page<TransferRequest> page = transferRequestRepository.pageByScope(branchScope, st, pageable);
		Map<Integer, String> branchNames = loadBranchNames(page.getContent());
		Map<Long, String> userNames = loadUserNames(page.getContent());
		return page.map(t -> toResponse(t, false, branchNames, userNames));
	}

	@Transactional(readOnly = true)
	public TransferResponseDto getById(long id, long userId, boolean isAdmin) {
		TransferRequest t = transferRequestRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Không tìm thấy yêu cầu."));
		assertCanView(t, userId, isAdmin);
		TransferResponseDto dto = toResponse(t, true);
		List<TransferApprovalHistory> hist = transferApprovalHistoryRepository.findByTransfer_IdOrderByActedAtAsc(id);
		List<TransferApprovalHistoryItemDto> items = new ArrayList<>();
		for (TransferApprovalHistory h : hist) {
			String name = userRepository.findById(h.getApprovedBy()).map(User::getName).orElse("");
			items.add(TransferApprovalHistoryItemDto.builder()
					.approvedBy(h.getApprovedBy())
					.approvedByName(name)
					.action(h.getAction())
					.note(h.getNote())
					.actedAt(h.getActedAt())
					.build());
		}
		dto.setApprovalHistory(items);
		return dto;
	}

	@Transactional
	public TransferResponseDto approve(long id, TransferActionRequestDto body, long adminUserId) {
		TransferRequest t = transferRequestRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Không tìm thấy yêu cầu."));
		// B1: Chỉ Pending
		if (!"Pending".equals(t.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS, "Chỉ yêu cầu Pending mới được phê duyệt.");
		}
		// B2: Cập nhật trạng thái
		t.setStatus("Approved");
		// B3: Ghi lịch sử duyệt
		TransferApprovalHistory h = new TransferApprovalHistory();
		h.setTransfer(t);
		h.setApprovedBy(adminUserId);
		h.setAction("Approved");
		h.setNote(body.getNote().trim());
		transferApprovalHistoryRepository.save(h);
		// B4: Cập nhật xe InTransfer (cùng transaction)
		vehicleService.applyTransferApprovedMarkInTransfer(t.getVehicle().getId(), t.getFromBranchId());
		transferRequestRepository.save(t);
		return toResponse(t, false);
	}

	@Transactional
	public TransferResponseDto reject(long id, TransferActionRequestDto body, long adminUserId) {
		TransferRequest t = transferRequestRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Không tìm thấy yêu cầu."));
		if (!"Pending".equals(t.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS, "Chỉ yêu cầu Pending mới được từ chối.");
		}
		t.setStatus("Rejected");
		TransferApprovalHistory h = new TransferApprovalHistory();
		h.setTransfer(t);
		h.setApprovedBy(adminUserId);
		h.setAction("Rejected");
		h.setNote(body.getNote().trim());
		transferApprovalHistoryRepository.save(h);
		transferRequestRepository.save(t);
		return toResponse(t, false);
	}

	@Transactional
	public TransferResponseDto complete(long id, CompleteTransferRequestDto body, long userId, boolean isAdmin) {
		if (isAdmin) {
			throw new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED, "Chỉ BranchManager chi nhánh đích được xác nhận nhận xe.");
		}
		int managerBranchId = requireManagerBranchId(userId);
		TransferRequest t = transferRequestRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND, "Không tìm thấy yêu cầu."));
		// B1: Đã Approved
		if (!"Approved".equals(t.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_TRANSFER_STATUS, "Chỉ yêu cầu Approved mới hoàn tất nhận xe.");
		}
		// B2: Manager thuộc chi nhánh đích
		if (managerBranchId != t.getToBranchId()) {
			throw new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED, "Chỉ manager chi nhánh nhận xe được xác nhận.");
		}
		// B3: Cập nhật request + xe (atomic)
		t.setStatus("Completed");
		vehicleService.applyTransferCompleteMoveToBranch(t.getVehicle().getId(), t.getFromBranchId(), t.getToBranchId());
		transferRequestRepository.save(t);
		// B4: Không ghi TransferApprovalHistory (constraint chỉ Approved/Rejected)
		return toResponse(t, false);
	}

	private void assertCanView(TransferRequest t, long userId, boolean isAdmin) {
		if (isAdmin) {
			return;
		}
		int b = requireManagerBranchId(userId);
		if (!Objects.equals(t.getFromBranchId(), b) && !Objects.equals(t.getToBranchId(), b)) {
			throw new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED, "Bạn không xem được yêu cầu này.");
		}
	}

	private int requireManagerBranchId(long userId) {
		return staffAssignmentRepository.findFirstByUserIdAndActiveTrueOrderByIdDesc(userId)
				.map(sa -> sa.getBranchId())
				.or(() -> branchRepository.findFirstByManager_IdAndDeletedFalse(userId).map(Branch::getId))
				.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED,
						"Không xác định được chi nhánh quản lý (StaffAssignments hoặc manager chi nhánh)."));
	}

	private TransferResponseDto toResponse(TransferRequest t, boolean includeHistoryPlaceholder) {
		return toResponse(t, includeHistoryPlaceholder, null, null);
	}

	private TransferResponseDto toResponse(TransferRequest t, boolean includeHistoryPlaceholder,
			Map<Integer, String> branchNames, Map<Long, String> userNames) {
		Vehicle v = t.getVehicle();
		String fromName = branchName(t.getFromBranchId(), branchNames);
		String toName = branchName(t.getToBranchId(), branchNames);
		String reqName = userName(t.getRequestedBy(), userNames);
		TransferResponseDto dto = TransferResponseDto.builder()
				.id(t.getId())
				.vehicleId(v.getId())
				.vehicleTitle(v.getTitle())
				.vehicleListingId(v.getListingId())
				.fromBranchId(t.getFromBranchId())
				.fromBranchName(fromName)
				.toBranchId(t.getToBranchId())
				.toBranchName(toName)
				.requestedBy(t.getRequestedBy())
				.requestedByName(reqName)
				.status(t.getStatus())
				.reason(t.getReason())
				.createdAt(t.getCreatedAt())
				.updatedAt(t.getUpdatedAt())
				.build();
		if (includeHistoryPlaceholder) {
			dto.setApprovalHistory(List.of());
		}
		return dto;
	}

	private String branchName(int id, Map<Integer, String> cache) {
		if (cache != null && cache.containsKey(id)) {
			return cache.get(id);
		}
		return branchRepository.findByIdAndDeletedFalse(id).map(Branch::getName).orElse("");
	}

	private String userName(long id, Map<Long, String> cache) {
		if (cache != null && cache.containsKey(id)) {
			return cache.get(id);
		}
		return userRepository.findById(id).map(User::getName).orElse("");
	}

	private Map<Integer, String> loadBranchNames(List<TransferRequest> rows) {
		Map<Integer, String> m = new HashMap<>();
		for (TransferRequest t : rows) {
			m.putIfAbsent(t.getFromBranchId(), null);
			m.putIfAbsent(t.getToBranchId(), null);
		}
		for (Integer bid : m.keySet()) {
			m.put(bid, branchRepository.findByIdAndDeletedFalse(bid).map(Branch::getName).orElse(""));
		}
		return m;
	}

	private Map<Long, String> loadUserNames(List<TransferRequest> rows) {
		Map<Long, String> m = new HashMap<>();
		for (TransferRequest t : rows) {
			m.putIfAbsent(t.getRequestedBy(), null);
		}
		for (Long uid : m.keySet()) {
			m.put(uid, userRepository.findById(uid).map(User::getName).orElse(""));
		}
		return m;
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

}
