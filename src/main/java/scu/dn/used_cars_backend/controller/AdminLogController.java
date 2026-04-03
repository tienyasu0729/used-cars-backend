package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.AdminLogPageResult;
import scu.dn.used_cars_backend.dto.admin.AdminLogRowDto;
import scu.dn.used_cars_backend.service.AdminLogService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

	private final AdminLogService adminLogService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<AdminLogRowDto>>> list(
			@RequestParam(required = false) String module,
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			@RequestParam(required = false) String action,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		AdminLogPageResult r = adminLogService.page(module, userId, fromDate, toDate, action, page, size);
		return ResponseEntity.ok(ApiResponse.success(r.getContent(), r.getMeta()));
	}
}
