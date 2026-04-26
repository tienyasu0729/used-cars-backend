package scu.dn.used_cars_backend.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import scu.dn.used_cars_backend.common.web.HttpServletClientIp;
import scu.dn.used_cars_backend.booking.dto.CreateDocumentSessionRequest;
import scu.dn.used_cars_backend.booking.dto.DocumentSessionResponse;
import scu.dn.used_cars_backend.booking.service.DocumentSessionService;
import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentSessionController {

	private final DocumentSessionService sessionService;

	@PostMapping("/document-sessions")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<DocumentSessionResponse>> create(
			@Valid @RequestBody CreateDocumentSessionRequest request,
			Authentication auth,
			HttpServletRequest httpRequest) {
		long userId = requireUserId(auth);
		String label = auth.getName() != null && !auth.getName().isBlank() ? auth.getName() : String.valueOf(userId);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(sessionService.createSession(request, userId, HttpServletClientIp.resolve(httpRequest), label)));
	}

	@GetMapping("/document-sessions/{sessionId}/poll")
	@PreAuthorize("hasRole('CUSTOMER')")
	public ResponseEntity<ApiResponse<DocumentSessionResponse>> poll(
			@PathVariable String sessionId, Authentication auth) {
		long userId = requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(sessionService.pollSession(sessionId, userId)));
	}

	@GetMapping("/public/document-sessions/{sessionId}")
	public ResponseEntity<ApiResponse<DocumentSessionResponse>> getPublic(
			@PathVariable String sessionId, @RequestParam String t) {
		return ResponseEntity.ok(ApiResponse.success(sessionService.getSessionPublic(sessionId, t)));
	}

	@PostMapping("/public/document-sessions/{sessionId}/upload")
	public ResponseEntity<ApiResponse<DocumentSessionResponse>> upload(
			@PathVariable String sessionId,
			@RequestParam String t,
			@RequestParam String fileUrl,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok(ApiResponse.success(
				sessionService.uploadDocument(sessionId, t, fileUrl, HttpServletClientIp.resolve(httpRequest))));
	}

	private static long requireUserId(Authentication authentication) {
		if (authentication == null || !(authentication.getDetails() instanceof Long userId)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return userId;
	}
}
