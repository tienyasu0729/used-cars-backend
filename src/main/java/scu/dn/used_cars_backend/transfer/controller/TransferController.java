package scu.dn.used_cars_backend.transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;
import scu.dn.used_cars_backend.transfer.dto.CompleteTransferRequestDto;
import scu.dn.used_cars_backend.transfer.dto.CreateTransferRequestDto;
import scu.dn.used_cars_backend.transfer.dto.TransferActionRequestDto;
import scu.dn.used_cars_backend.transfer.dto.TransferResponseDto;
import scu.dn.used_cars_backend.transfer.service.TransferService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferService transferService;

	@PostMapping
	@PreAuthorize("hasRole('ADMIN') or hasAuthority('PERMISSION_TRANSFERS_CREATE')")
	public ResponseEntity<ApiResponse<TransferResponseDto>> create(@Valid @RequestBody CreateTransferRequestDto body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		TransferResponseDto dto = transferService.create(body, userId, isAdmin(authentication));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(dto));
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN') or hasAuthority('PERMISSION_TRANSFERS_VIEW')")
	public ResponseEntity<ApiResponse<List<TransferResponseDto>>> list(
			@RequestParam(required = false) String status,
			@PageableDefault(size = 10) Pageable pageable,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		Page<TransferResponseDto> page = transferService.list(status, pageable, userId, isAdmin(authentication));
		PageMetaDto meta = PageMetaDto.builder()
				.page(page.getNumber())
				.size(page.getSize())
				.totalElements(page.getTotalElements())
				.totalPages(page.getTotalPages())
				.build();
		return ResponseEntity.ok(ApiResponse.success(page.getContent(), meta));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN') or hasAuthority('PERMISSION_TRANSFERS_VIEW')")
	public ResponseEntity<ApiResponse<TransferResponseDto>> getOne(@PathVariable long id,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(transferService.getById(id, userId, isAdmin(authentication))));
	}

	// Chỉ BranchManager của chi nhánh nguồn mới được phê duyệt.
	// Admin KHÔNG được duyệt (service sẽ chặn vì admin không thuộc chi nhánh nào)
	// → Controller cũng chặn sớm để thống nhất quyền và trả 403 đúng ngữ cảnh.
	@PostMapping("/{id}/approve")
	@PreAuthorize("hasAuthority('PERMISSION_TRANSFERS_APPROVE')")
	public ResponseEntity<ApiResponse<TransferResponseDto>> approve(@PathVariable long id,
			@Valid @RequestBody TransferActionRequestDto body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(transferService.approve(id, body, userId)));
	}

	// Tương tự approve: chỉ BranchManager chi nhánh nguồn được từ chối.
	@PostMapping("/{id}/reject")
	@PreAuthorize("hasAuthority('PERMISSION_TRANSFERS_APPROVE')")
	public ResponseEntity<ApiResponse<TransferResponseDto>> reject(@PathVariable long id,
			@Valid @RequestBody TransferActionRequestDto body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		return ResponseEntity.ok(ApiResponse.success(transferService.reject(id, body, userId)));
	}

	@PatchMapping("/{id}/complete")
	@PreAuthorize("hasRole('BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<TransferResponseDto>> complete(@PathVariable long id,
			@Valid @RequestBody(required = false) CompleteTransferRequestDto body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		CompleteTransferRequestDto b = body != null ? body : new CompleteTransferRequestDto();
		return ResponseEntity.ok(ApiResponse.success(transferService.complete(id, b, userId, isAdmin(authentication))));
	}

	private static long requireUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		if (!(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return userId;
	}

	private static boolean isAdmin(Authentication authentication) {
		for (GrantedAuthority a : authentication.getAuthorities()) {
			if ("ROLE_ADMIN".equals(a.getAuthority())) {
				return true;
			}
		}
		return false;
	}

}
