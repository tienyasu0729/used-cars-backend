package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.branch.BranchPublicDto;
import scu.dn.used_cars_backend.dto.branch.BranchTeamMemberDto;
import scu.dn.used_cars_backend.entity.Branch;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.BranchRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BranchService {

	private final BranchRepository branchRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<BranchPublicDto> listPublic() {
		return branchRepository.findAllByDeletedFalseOrderByIdAsc().stream()
				.map(BranchService::toPublicDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public BranchPublicDto getPublicById(int id) {
		Branch b = branchRepository.findByIdAndDeletedFalse(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));
		return toPublicDto(b);
	}

	/**
	 * Đội ngũ công khai: quản lý (Branches.manager_id) + nhân viên có StaffAssignments active tại chi nhánh.
	 */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listPublicTeam(int branchId) {
		Branch branch = branchRepository.findActiveByIdWithManager(branchId)
				.orElseThrow(() -> new BusinessException(ErrorCode.BRANCH_NOT_FOUND, "Không tìm thấy chi nhánh."));

		List<BranchTeamMemberDto> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();

		User manager = branch.getManager();
		if (manager != null && !Boolean.TRUE.equals(manager.getDeleted())
				&& "active".equalsIgnoreCase(manager.getStatus())) {
			out.add(toTeamMember(manager, "Quản lý chi nhánh"));
			seen.add(manager.getId());
		}

		for (User u : userRepository.findActiveStaffUsersByBranchId(branchId)) {
			if (seen.contains(u.getId())) {
				continue;
			}
			if (!hasSalesOrManagerRole(u)) {
				continue;
			}
			out.add(toTeamMember(u, displayRoleForPublic(u)));
			seen.add(u.getId());
		}

		return out;
	}

	private static boolean hasSalesOrManagerRole(User u) {
		return u.getUserRoles().stream().map(ur -> ur.getRole().getName()).anyMatch(BranchService::isSalesOrManagerRoleName);
	}

	private static boolean isSalesOrManagerRoleName(String roleName) {
		return "SalesStaff".equals(roleName) || "BranchManager".equals(roleName);
	}

	private static String displayRoleForPublic(User u) {
		return u.getUserRoles().stream()
				.filter(ur -> isSalesOrManagerRoleName(ur.getRole().getName()))
				.min(Comparator.comparingInt(ur -> ur.getRole().getId()))
				.map(ur -> mapRoleNameToVietnamese(ur.getRole().getName()))
				.orElse("Nhân viên");
	}

	private static String mapRoleNameToVietnamese(String roleName) {
		if (roleName == null) {
			return "Nhân viên";
		}
		return switch (roleName) {
			case "BranchManager" -> "Quản lý chi nhánh";
			case "SalesStaff" -> "Tư vấn viên";
			default -> "Nhân viên";
		};
	}

	private static BranchTeamMemberDto toTeamMember(User u, String roleLabel) {
		return BranchTeamMemberDto.builder()
				.name(u.getName())
				.role(roleLabel)
				.avatarUrl(blankToNull(u.getAvatarUrl()))
				.build();
	}

	private static String blankToNull(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.trim();
	}

	private static BranchPublicDto toPublicDto(Branch b) {
		return BranchPublicDto.builder()
				.id(b.getId())
				.name(b.getName())
				.address(b.getAddress())
				.phone(b.getPhone())
				.lat(b.getLat())
				.lng(b.getLng())
				.build();
	}

}
