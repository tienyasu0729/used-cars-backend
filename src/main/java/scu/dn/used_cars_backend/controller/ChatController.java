package scu.dn.used_cars_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

import scu.dn.used_cars_backend.common.api.ApiResponse;
import scu.dn.used_cars_backend.dto.branch.BranchTeamMemberDto;
import scu.dn.used_cars_backend.dto.chat.ChatConversationRowDto;
import scu.dn.used_cars_backend.dto.chat.ChatMessageRowDto;
import scu.dn.used_cars_backend.dto.chat.ChatTransferCandidateDto;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationRequest;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationResponse;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageRequest;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageResponse;
import scu.dn.used_cars_backend.dto.chat.TransferChatConversationRequest;
import scu.dn.used_cars_backend.security.AuthenticationDetailsUtils;
import scu.dn.used_cars_backend.service.ChatService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

	private final ChatService chatService;

	@GetMapping("/conversations")
	public ResponseEntity<ApiResponse<List<ChatConversationRowDto>>> listConversations(Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(chatService.listConversations(uid)));
	}

	/** QL: NV/QL cùng chi nhánh + chi nhánh khác (tài khoản active) để mở chat nội bộ. */
	@GetMapping("/manager-contact-options")
	@PreAuthorize("hasRole('BRANCHMANAGER')")
	public ResponseEntity<ApiResponse<List<BranchTeamMemberDto>>> managerChatContacts(Authentication auth) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(chatService.listManagerChatContactOptions(uid)));
	}

	@PostMapping("/conversations")
	public ResponseEntity<ApiResponse<CreateChatConversationResponse>> createConversation(Authentication auth,
			@Valid @RequestBody CreateChatConversationRequest body) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		CreateChatConversationResponse r = chatService.createConversation(uid, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r));
	}

	@GetMapping("/conversations/{id}/messages")
	public ResponseEntity<ApiResponse<List<ChatMessageRowDto>>> listMessages(Authentication auth, @PathVariable long id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		Page<ChatMessageRowDto> pg = chatService.listMessages(uid, id, page, size);
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("page", pg.getNumber());
		meta.put("size", pg.getSize());
		meta.put("total", pg.getTotalElements());
		meta.put("totalPages", pg.getTotalPages());
		return ResponseEntity.ok(ApiResponse.success(pg.getContent(), meta));
	}

	@PostMapping("/messages")
	public ResponseEntity<ApiResponse<SendChatMessageResponse>> sendMessage(Authentication auth,
			@Valid @RequestBody SendChatMessageRequest body) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		SendChatMessageResponse r = chatService.sendMessage(uid, body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r));
	}

	@GetMapping("/conversations/{id}/transfer-candidates")
	public ResponseEntity<ApiResponse<List<ChatTransferCandidateDto>>> transferCandidates(Authentication auth,
			@PathVariable long id) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		return ResponseEntity.ok(ApiResponse.success(chatService.listTransferCandidates(uid, id)));
	}

	@PostMapping("/conversations/{id}/transfer")
	public ResponseEntity<ApiResponse<Void>> transferConversation(Authentication auth, @PathVariable long id,
			@Valid @RequestBody TransferChatConversationRequest body) {
		long uid = AuthenticationDetailsUtils.requireUserId(auth);
		chatService.transferConversationToColleague(uid, id, body);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
