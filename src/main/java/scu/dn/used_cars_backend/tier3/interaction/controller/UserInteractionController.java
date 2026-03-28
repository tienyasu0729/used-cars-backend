package scu.dn.used_cars_backend.tier3.interaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.tier3.interaction.dto.MergeViewHistoryDataResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.MergeViewHistoryRequest;
import scu.dn.used_cars_backend.tier3.interaction.dto.MessageDataResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.SaveVehicleRequest;
import scu.dn.used_cars_backend.tier3.interaction.dto.SavedVehicleResponse;
import scu.dn.used_cars_backend.tier3.interaction.dto.ViewHistoryResponse;
import scu.dn.used_cars_backend.tier3.interaction.service.UserInteractionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserInteractionController {

	private static final String GUEST_HEADER = "X-Guest-Id";

	private final UserInteractionService userInteractionService;

	@PostMapping("/users/me/saved-vehicles")
	public ResponseEntity<ApiResponse<MessageDataResponse>> saveSaved(@Valid @RequestBody SaveVehicleRequest body,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		MessageDataResponse data = userInteractionService.saveVehicle(userId, body.getVehicleId());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
	}

	@DeleteMapping("/users/me/saved-vehicles/{vehicleId}")
	public ResponseEntity<ApiResponse<MessageDataResponse>> unsaveSaved(@PathVariable long vehicleId,
			Authentication authentication) {
		long userId = requireUserId(authentication);
		MessageDataResponse data = userInteractionService.unsaveVehicle(userId, vehicleId);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@GetMapping("/users/me/saved-vehicles")
	public ResponseEntity<ApiResponse<List<SavedVehicleResponse>>> listSaved(Authentication authentication) {
		long userId = requireUserId(authentication);
		List<SavedVehicleResponse> data = userInteractionService.listSavedVehicles(userId);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@PostMapping("/vehicles/{id}/view")
	public ResponseEntity<ApiResponse<Void>> recordView(@PathVariable("id") long vehicleId,
			@RequestHeader(value = GUEST_HEADER, required = false) String guestId, Authentication authentication) {
		Long userId = extractUserId(authentication);
		userInteractionService.recordView(guestId, userId, vehicleId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	@GetMapping("/vehicles/recently-viewed")
	public ResponseEntity<ApiResponse<List<ViewHistoryResponse>>> recentlyViewed(
			@RequestHeader(value = GUEST_HEADER, required = false) String guestId, Authentication authentication) {
		Long userId = extractUserId(authentication);
		List<ViewHistoryResponse> data = userInteractionService.recentlyViewed(guestId, userId);
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	@PostMapping("/users/me/merge-view-history")
	public ResponseEntity<ApiResponse<MergeViewHistoryDataResponse>> mergeHistory(
			@Valid @RequestBody MergeViewHistoryRequest body, Authentication authentication) {
		long userId = requireUserId(authentication);
		MergeViewHistoryDataResponse data = userInteractionService.mergeGuestViewHistory(userId, body.getGuestId());
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	private static long requireUserId(Authentication authentication) {
		Long id = extractUserId(authentication);
		if (id == null) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Yêu cầu đăng nhập.");
		}
		return id;
	}

	private static Long extractUserId(Authentication authentication) {
		if (authentication == null || authentication.getDetails() == null) {
			return null;
		}
		if (authentication.getDetails() instanceof Long userId) {
			return userId;
		}
		return null;
	}
}
