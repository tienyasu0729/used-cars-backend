package scu.dn.used_cars_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.sales.ShowroomCustomerInfo;
import scu.dn.used_cars_backend.entity.Role;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.entity.UserRole;
import scu.dn.used_cars_backend.repository.RoleRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.util.List;

/**
 * Tìm hoặc tạo user Customer cho luồng showroom (offline).
 * Được gọi bên trong @Transactional của DepositService / OrderService.
 */
@Service
@RequiredArgsConstructor
public class ShowroomCustomerService {

	private static final String AUTH_PROVIDER_SHOWROOM = "showroom";

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	/**
	 * Resolve customerId từ ShowroomCustomerInfo:
	 * 1. Tìm user Customer active theo email hoặc phone.
	 * 2. Nếu tìm thấy đúng 1 → cập nhật name/address → trả về id.
	 * 3. Nếu tìm thấy >1 → throw CUSTOMER_IDENTITY_AMBIGUOUS.
	 * 4. Nếu không tìm thấy → tạo mới user Customer (passwordHash=null, authProvider=showroom).
	 */
	public long findOrCreate(ShowroomCustomerInfo info) {
		String email = info.getEmail().trim().toLowerCase();
		String phone = normalizePhone(info.getPhone());

		List<User> matches = userRepository.findActiveCustomersByEmailOrPhone(email, phone);

		if (matches.size() > 1) {
			throw new BusinessException(ErrorCode.CUSTOMER_IDENTITY_AMBIGUOUS,
					"Tìm thấy nhiều khách hàng khớp email/SĐT. Vui lòng liên hệ Admin để kiểm tra.");
		}

		if (matches.size() == 1) {
			User existing = matches.get(0);
			existing.setName(info.getFullName().trim());
			existing.setAddress(info.getAddress().trim());
			if (existing.getPhone() == null || !existing.getPhone().equalsIgnoreCase(phone)) {
				existing.setPhone(phone);
			}
			userRepository.save(existing);
			return existing.getId();
		}

		return createShowroomUser(info, email, phone);
	}

	private long createShowroomUser(ShowroomCustomerInfo info, String email, String phone) {
		if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
			throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS,
					"Email đã được sử dụng bởi tài khoản khác (không phải Customer active). Vui lòng kiểm tra lại.");
		}

		Role customerRole = roleRepository.findByName("Customer")
				.orElseThrow(() -> new IllegalStateException("Vai trò Customer chưa được seed trong database."));

		User user = new User();
		user.setName(info.getFullName().trim());
		user.setEmail(email);
		user.setPhone(phone);
		user.setAddress(info.getAddress().trim());
		user.setPasswordHash(null);
		user.setAuthProvider(AUTH_PROVIDER_SHOWROOM);
		user.setStatus("active");
		user.setDeleted(false);
		user.setPasswordChangeRequired(false);

		UserRole link = new UserRole();
		link.setUser(user);
		link.setRole(customerRole);
		user.getUserRoles().add(link);

		userRepository.save(user);
		return user.getId();
	}

	static String normalizePhone(String raw) {
		if (raw == null) return null;
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
