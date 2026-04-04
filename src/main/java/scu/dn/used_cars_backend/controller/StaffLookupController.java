package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.sales.CustomerOptionDto;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffLookupController {

	private final UserRepository userRepository;

	@GetMapping("/customer-options")
	@PreAuthorize("hasAnyRole('SALESSTAFF','BRANCHMANAGER','ADMIN')")
	public ResponseEntity<ApiResponse<List<CustomerOptionDto>>> customerOptions() {
		List<CustomerOptionDto> rows = userRepository.findActiveCustomersWithRoles().stream()
				.sorted(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER))
				.limit(500)
				.map(u -> CustomerOptionDto.builder()
						.id(String.valueOf(u.getId()))
						.name(u.getName())
						.phone(u.getPhone())
						.email(u.getEmail())
						.build())
				.toList();
		return ResponseEntity.ok(ApiResponse.success(rows));
	}
}
