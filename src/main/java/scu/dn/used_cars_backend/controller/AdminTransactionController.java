package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.admin.TransactionDetailDto;
import scu.dn.used_cars_backend.dto.admin.TransactionRowDto;
import scu.dn.used_cars_backend.dto.admin.TransactionSummaryDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.security.JwtRoleNames;
import scu.dn.used_cars_backend.service.AdminTransactionService;
import scu.dn.used_cars_backend.service.AdminTransactionService.TransactionFilter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BRANCHMANAGER')")
public class AdminTransactionController {

	private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

	private final AdminTransactionService transactionService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<TransactionRowDto>>> list(
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String gateway,
			@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = JwtRoleNames.isAdmin(authentication);
		TransactionFilter filter = new TransactionFilter(source, status, gateway, branchId, keyword, from, to, page,
				size);
		var paged = transactionService.page(filter, userId, admin);
		return ResponseEntity.ok(ApiResponse.success(paged.items(), paged.meta()));
	}

	@GetMapping("/summary")
	public ResponseEntity<ApiResponse<TransactionSummaryDto>> summary(
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String gateway,
			@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = JwtRoleNames.isAdmin(authentication);
		TransactionFilter filter = new TransactionFilter(source, status, gateway, branchId, keyword, from, to, 0, 20);
		return ResponseEntity.ok(ApiResponse.success(transactionService.summary(filter, userId, admin)));
	}

	@GetMapping("/{source}/{id}")
	public ResponseEntity<ApiResponse<TransactionDetailDto>> detail(@PathVariable String source,
			@PathVariable long id, Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = JwtRoleNames.isAdmin(authentication);
		return ResponseEntity.ok(ApiResponse.success(transactionService.detail(source, id, userId, admin)));
	}

	@GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
	public ResponseEntity<byte[]> export(
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String gateway,
			@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			Authentication authentication) {
		long userId = AuthenticationDetailsUtils.requireUserId(authentication);
		boolean admin = JwtRoleNames.isAdmin(authentication);
		TransactionFilter filter = new TransactionFilter(source, status, gateway, branchId, keyword, from, to, 0,
				5000);
		byte[] body = transactionService.exportCsv(filter, userId, admin);
		String day = LocalDate.now(VN).format(DateTimeFormatter.BASIC_ISO_DATE);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions_" + day + ".csv\"")
				.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
				.body(body);
	}
}
