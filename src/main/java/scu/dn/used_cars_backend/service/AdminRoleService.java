package scu.dn.used_cars_backend.service;

// Service quản lý vai trò custom + xem role hệ thống — RolePermissions, userCount.

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.admin.AdminRoleListItemDto;
import scu.dn.used_cars_backend.dto.admin.CreateAdminRoleRequest;
import scu.dn.used_cars_backend.dto.admin.UpdateAdminRoleRequest;
import scu.dn.used_cars_backend.entity.Permission;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.RolePermission;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.PermissionRepository;
import scu.dn.used_cars_backend.repository.RolePermissionRepository;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;
import scu.dn.used_cars_backend.repository.UserRoleRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRoleService {

	private final RoleRepository roleRepository;
	private final RolePermissionRepository rolePermissionRepository;
	private final PermissionRepository permissionRepository;
	private final UserRoleRepository userRoleRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<AdminRoleListItemDto> listRoles() {
		List<Role> roles = roleRepository.findAll();
		List<AdminRoleListItemDto> out = new ArrayList<>();
		for (Role r : roles) {
			long uc = userRoleRepository.countByRole_Id(r.getId());
			List<RolePermission> rps = rolePermissionRepository.findAllByRole_IdOrderByPermission_IdAsc(r.getId());
			List<String> permNames = rps.stream()
					.map(rp -> rp.getPermission().getModule() + "." + rp.getPermission().getAction())
					.collect(Collectors.toList());
			List<Integer> permIds = rps.stream().map(rp -> rp.getPermission().getId()).collect(Collectors.toList());
			out.add(AdminRoleListItemDto.builder()
					.id(r.getId())
					.name(r.getName())
					.userCount(uc)
					.permissionIds(permIds)
					.permissions(permNames)
					.systemRole(Boolean.TRUE.equals(r.getSystemRole()))
					.build());
		}
		return out;
	}

	@Transactional
	public AdminRoleListItemDto createRole(CreateAdminRoleRequest req) {
		String name = req.getName().trim();
		if (roleRepository.existsByNameIgnoreCase(name)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên vai trò đã tồn tại.");
		}
		List<Integer> ids = req.getPermissionIds() == null ? List.of() : req.getPermissionIds();
		List<Permission> perms = loadPermissionsOrThrow(ids);
		Role role = new Role();
		role.setName(name);
		role.setDescription(null);
		role.setSystemRole(false);
		Role saved = roleRepository.save(role);
		saveRolePermissions(saved, perms);
		return toDto(saved);
	}

	@Transactional
	public AdminRoleListItemDto updateRole(int roleId, UpdateAdminRoleRequest req) {
		Role role = roleRepository.findById(roleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò."));
		assertUpdatableRole(role);
		String name = req.getName().trim();
		if (Boolean.TRUE.equals(role.getSystemRole())) {
			if (!role.getName().trim().equalsIgnoreCase(name)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể đổi tên vai trò hệ thống.");
			}
			name = role.getName();
		}
		Optional<Role> sameName = roleRepository.findByNameIgnoreCase(name);
		if (sameName.isPresent() && !sameName.get().getId().equals(roleId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên vai trò đã tồn tại.");
		}
		List<Permission> perms = loadPermissionsOrThrow(
				req.getPermissionIds() == null ? List.of() : req.getPermissionIds());
		role.setName(name);
		roleRepository.save(role);
		rolePermissionRepository.deleteAllByRole_Id(roleId);
		saveRolePermissions(role, perms);
		return toDto(role);
	}

	@Transactional
	public void deleteRole(int roleId) {
		Role role = roleRepository.findById(roleId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò."));
		assertDeletableRole(role);
		Role customer = roleRepository.findByNameIgnoreCase("Customer")
				.orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò Customer."));
		if (customer.getId() != null && customer.getId().equals(roleId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể xóa vai trò Customer.");
		}
		List<UserRole> links = userRoleRepository.findAllByRole_Id(roleId);
		Set<Long> touchedUsers = new HashSet<>();
		for (UserRole ur : links) {
			touchedUsers.add(ur.getUser().getId());
		}
		userRoleRepository.deleteAll(links);
		userRoleRepository.flush();
		for (Long uid : touchedUsers) {
			if (userRoleRepository.countByUser_Id(uid) == 0) {
				User u = userRepository.getReferenceById(uid);
				UserRole link = new UserRole();
				link.setUser(u);
				link.setRole(customer);
				userRoleRepository.save(link);
			}
		}
		rolePermissionRepository.deleteAllByRole_Id(roleId);
		roleRepository.delete(role);
	}

	private void assertDeletableRole(Role role) {
		if (Boolean.TRUE.equals(role.getSystemRole())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể xóa vai trò hệ thống.");
		}
	}

	private void assertUpdatableRole(Role role) {
		if (isAdminSystemRole(role)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể sửa quyền vai trò Admin.");
		}
		if (!Boolean.TRUE.equals(role.getSystemRole())) {
			return;
		}
		if (isCoreSystemRoleWithEditablePermissions(role)) {
			return;
		}
		throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể sửa vai trò hệ thống này.");
	}

	private boolean isAdminSystemRole(Role role) {
		return role.getName() != null && "admin".equalsIgnoreCase(role.getName().trim());
	}

	private boolean isCoreSystemRoleWithEditablePermissions(Role role) {
		if (role.getName() == null) {
			return false;
		}
		String n = role.getName().trim().toLowerCase();
		return "customer".equals(n) || "salesstaff".equals(n) || "branchmanager".equals(n);
	}

	private List<Permission> loadPermissionsOrThrow(List<Integer> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		List<Permission> perms = permissionRepository.findAllByIdIn(ids);
		if (perms.size() != ids.size()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Có permission id không tồn tại.");
		}
		return perms;
	}

	private void saveRolePermissions(Role role, List<Permission> perms) {
		for (Permission p : perms) {
			RolePermission rp = new RolePermission();
			rp.setRole(role);
			rp.setPermission(p);
			rolePermissionRepository.save(rp);
		}
	}

	private AdminRoleListItemDto toDto(Role r) {
		long uc = userRoleRepository.countByRole_Id(r.getId());
		List<RolePermission> rps = rolePermissionRepository.findAllByRole_IdOrderByPermission_IdAsc(r.getId());
		List<String> permNames = rps.stream()
				.map(rp -> rp.getPermission().getModule() + "." + rp.getPermission().getAction())
				.collect(Collectors.toList());
		List<Integer> permIds = rps.stream().map(rp -> rp.getPermission().getId()).collect(Collectors.toList());
		return AdminRoleListItemDto.builder()
				.id(r.getId())
				.name(r.getName())
				.userCount(uc)
				.permissionIds(permIds)
				.permissions(permNames)
				.systemRole(Boolean.TRUE.equals(r.getSystemRole()))
				.build();
	}
}
