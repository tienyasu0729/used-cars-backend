package scu.dn.used_cars_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.notification.InAppNotificationRowDto;
import scu.dn.used_cars_backend.dto.notification.UnreadCountDto;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.InAppNotificationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final InAppNotificationService inAppNotificationService;

	@GetMapping
	public ResponseEntity<ApiResponse<List<InAppNotificationRowDto>>> list(Authentication auth,
			@RequestParam(name = "is_read", required = false) Boolean isRead,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		long userId = AuthenticationDetailsUtils.requireUserId(auth);
		var pg = inAppNotificationService.listForUser(userId, isRead, page, size);
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("page", pg.getNumber());
		meta.put("size", pg.getSize());
		meta.put("total", pg.getTotalElements());
		meta.put("totalPages", pg.getTotalPages());
		return ResponseEntity.ok(ApiResponse.success(pg.getContent(), meta));
	}

	@GetMapping("/unread-count")
	public ResponseEntity<ApiResponse<UnreadCountDto>> unreadCount(Authentication auth) {
		long userId = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(inAppNotificationService.unreadCount(userId)));
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> markRead(Authentication auth, @PathVariable long id) {
		long userId = AuthenticationDetailsUtils.requireUserId(auth);
		inAppNotificationService.markRead(userId, id);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}

	@PatchMapping("/read-all")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> markAllRead(Authentication auth) {
		long userId = AuthenticationDetailsUtils.requireUserId(auth);
		inAppNotificationService.markAllRead(userId);
		return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
	}
}
