package scu.dn.used_cars_backend.service;

// Service CRUD chi nhánh cho Admin — đếm xe / nhân sự active.

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.AdminBranchListItemDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminBranchRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminBranchRequest;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.StaffAssignmentRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminBranchService {

	private static final Set<String> BRANCH_STATUSES = Set.of("active", "inactive");

	private final BranchRepository branchRepository;
	private final VehicleRepository vehicleRepository;
	private final StaffAssignmentRepository staffAssignmentRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public List<AdminBranchListItemDto> listBranches() {
		List<Branch> branches = branchRepository.findAllByDeletedFalseOrderByIdAsc();
		List<AdminBranchListItemDto> out = new ArrayList<>(branches.size());
		for (Branch b : branches) {
			long v = vehicleRepository.countByBranch_IdAndDeletedFalse(b.getId());
			long s = staffAssignmentRepository.countByBranchIdAndActiveTrue(b.getId());
			String mgrName = "";
			if (b.getManager() != null) {
				mgrName = b.getManager().getName();
			}
			out.add(AdminBranchListItemDto.builder()
					.id(b.getId())
					.name(b.getName())
					.address(b.getAddress())
					.phone(b.getPhone() != null ? b.getPhone() : "")
					.managerName(mgrName)
					.status(b.getStatus())
					.vehicleCount(v)
					.staffCount(s)
					.imageUrl(firstShowroomImageUrl(b.getShowroomImageUrlsJson()))
					.build());
		}
		return out;
	}

	@Transactional
	public AdminBranchListItemDto createBranch(CreateAdminBranchRequest req) {
		Branch b = new Branch();
		b.setName(req.getName().trim());
		b.setAddress(req.getAddress().trim());
		b.setPhone(trimOrNull(req.getPhone()));
		b.setStatus("active");
		b.setDeleted(false);
		if (req.getManagerId() != null) {
			User m = userRepository.findByIdAndDeletedFalse(req.getManagerId())
					.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy quản lý."));
			b.setManager(m);
		}
		Branch saved = branchRepository.save(b);
		return toDto(saved);
	}

	@Transactional
	public AdminBranchListItemDto updateBranch(int branchId, UpdateAdminBranchRequest req) {
		Branch b = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		String st = req.getStatus().trim().toLowerCase();
		if (!BRANCH_STATUSES.contains(st)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trạng thái chi nhánh không hợp lệ.");
		}
		b.setName(req.getName().trim());
		b.setAddress(req.getAddress().trim());
		b.setPhone(trimOrNull(req.getPhone()));
		b.setStatus(st);
		branchRepository.save(b);
		return toDto(b);
	}

	@Transactional
	public void softDeleteBranch(int branchId) {
		Branch b = branchRepository.findByIdAndDeletedFalse(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		b.setDeleted(true);
		branchRepository.save(b);
	}

	private AdminBranchListItemDto toDto(Branch b) {
		long v = vehicleRepository.countByBranch_IdAndDeletedFalse(b.getId());
		long s = staffAssignmentRepository.countByBranchIdAndActiveTrue(b.getId());
		String mgrName = b.getManager() != null ? b.getManager().getName() : "";
		return AdminBranchListItemDto.builder()
				.id(b.getId())
				.name(b.getName())
				.address(b.getAddress())
				.phone(b.getPhone() != null ? b.getPhone() : "")
				.managerName(mgrName)
				.status(b.getStatus())
				.vehicleCount(v)
				.staffCount(s)
				.imageUrl(firstShowroomImageUrl(b.getShowroomImageUrlsJson()))
				.build();
	}

	/** Đồng bộ cách đọc với BranchService (JSON mảng URL). */
	private List<String> readShowroomImageUrls(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			JavaType listStringType = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
			List<String> raw = objectMapper.readValue(json, listStringType);
			return raw.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	private String firstShowroomImageUrl(String json) {
		List<String> urls = readShowroomImageUrls(json);
		return urls.isEmpty() ? null : urls.get(0);
	}

	private static String trimOrNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.trim();
	}
}
