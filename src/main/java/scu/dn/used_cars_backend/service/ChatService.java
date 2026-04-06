package scu.dn.used_cars_backend.service;

// Chat 1-1: hội thoại, tin nhắn, đếm chưa đọc (Sprint 9). WebSocket ghi chú hoãn — FE polling.
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.branch.BranchTeamMemberDto;
import scu.dn.used_cars_backend.dto.chat.ChatConversationRowDto;
import scu.dn.used_cars_backend.dto.chat.ChatMessageRowDto;
import scu.dn.used_cars_backend.dto.chat.ChatTransferCandidateDto;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationRequest;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationResponse;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageRequest;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageResponse;
import scu.dn.used_cars_backend.dto.chat.TransferChatConversationRequest;
import scu.dn.used_cars_backend.entity.ChatConversation;
import scu.dn.used_cars_backend.entity.ChatMessage;
import scu.dn.used_cars_backend.entity.ChatParticipant;
import scu.dn.used_cars_backend.entity.Consultation;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.ChatConversationRepository;
import scu.dn.used_cars_backend.repository.ChatMessageRepository;
import scu.dn.used_cars_backend.repository.ChatParticipantRepository;
import scu.dn.used_cars_backend.repository.ConsultationRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatService {

	private final ChatConversationRepository chatConversationRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;
	private final ConsultationRepository consultationRepository;
	private final BranchService branchService;
	private final StaffService staffService;

	@Transactional(readOnly = true)
	public List<ChatConversationRowDto> listConversations(long currentUserId) {
		User viewer = userRepository.findActiveByIdWithRoles(currentUserId).orElse(null);
		String viewerRole = viewer != null ? primaryRoleLabel(viewer) : "";
		boolean staffViewer = isStaffOrAdminChatRole(viewerRole);

		List<ChatParticipant> mine = chatParticipantRepository.findByUserId(currentUserId);
		List<ChatConversationRowDto> rows = new ArrayList<>();
		for (ChatParticipant row : mine) {
			Long cid = row.getConversationId();
			ChatConversation conv = chatConversationRepository.findById(cid).orElse(null);
			if (conv == null) {
				continue;
			}
			List<ChatParticipant> parts = chatParticipantRepository.findByConversationId(cid);
			Long otherId = parts.stream().map(ChatParticipant::getUserId).filter(uid -> !uid.equals(currentUserId))
					.findFirst().orElse(null);
			if (otherId == null) {
				continue;
			}
			User other = userRepository.findActiveByIdWithRoles(otherId).orElse(null);
			if (other == null) {
				continue;
			}
			String lastText = chatMessageRepository.findFirstByConversationIdOrderBySentAtDesc(cid).map(ChatMessage::getContent)
					.orElse("");
			var dtoBuilder = ChatConversationRowDto.builder()
					.id(cid)
					.participantName(other.getName())
					.participantRole(primaryRoleLabel(other))
					.lastMessage(lastText)
					.lastMessageAt(conv.getLastMessageAt())
					.unreadCount(row.getUnreadCount());
			if (staffViewer && viewer != null) {
				applyPendingConsultationOverlay(dtoBuilder, viewer, viewerRole, other);
			}
			rows.add(dtoBuilder.build());
		}
		rows.sort(Comparator.comparing(ChatConversationRowDto::getLastMessageAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.reversed());
		return rows;
	}

	/** Danh bạ chat: cùng chi nhánh + NV/QL chi nhánh khác (active) — chỉ BranchManager. */
	@Transactional(readOnly = true)
	public List<BranchTeamMemberDto> listManagerChatContactOptions(long actorUserId) {
		User actor = userRepository.findActiveByIdWithRoles(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!"BranchManager".equals(primaryRoleLabel(actor))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ quản lý chi nhánh dùng danh bạ này.");
		}
		int bid = staffService.getManagerBranchId(actorUserId);
		return branchService.listManagerCrossBranchChatContacts(bid, actorUserId);
	}

	private static boolean isStaffOrAdminChatRole(String viewerRole) {
		return "SalesStaff".equals(viewerRole) || "BranchManager".equals(viewerRole) || "Admin".equals(viewerRole);
	}

	/** Gắn phiếu tư vấn pending (nếu có và NV được phép) để nút “Tiếp nhận ngay” trên UI chat. */
	private void applyPendingConsultationOverlay(ChatConversationRowDto.ChatConversationRowDtoBuilder b, User viewer,
			String viewerRole, User otherParticipant) {
		if (!"Customer".equals(primaryRoleLabel(otherParticipant))) {
			return;
		}
		consultationRepository.findTopByCustomer_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(otherParticipant.getId(), "pending")
				.filter(c -> consultationVisibleToStaffViewer(c, viewer, viewerRole))
				.ifPresent(c -> {
					b.consultationId(c.getId());
					b.consultationStatus(c.getStatus());
					if (c.getVehicle() != null) {
						b.vehicleInfo(c.getVehicle().getTitle());
						b.vehiclePrice(formatListingPriceVnd(c.getVehicle().getPrice()));
					}
					b.consultationNewLead(Boolean.TRUE);
				});
	}

	private boolean consultationVisibleToStaffViewer(Consultation c, User viewer, String viewerRole) {
		if (c.getStatus() == null || !"pending".equalsIgnoreCase(c.getStatus().trim())) {
			return false;
		}
		if ("Admin".equals(viewerRole)) {
			return true;
		}
		if (c.getVehicle() == null) {
			return false;
		}
		try {
			int bid = staffService.getManagerBranchId(viewer.getId());
			return c.getVehicle().getBranch().getId() == bid;
		} catch (BusinessException ex) {
			return false;
		}
	}

	private static String formatListingPriceVnd(BigDecimal price) {
		if (price == null) {
			return null;
		}
		return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫";
	}

	private static String primaryRoleLabel(User u) {
		if (u.getUserRoles() == null || u.getUserRoles().isEmpty()) {
			return "User";
		}
		return u.getUserRoles().iterator().next().getRole().getName();
	}

	@Transactional
	public CreateChatConversationResponse createConversation(long currentUserId, CreateChatConversationRequest req) {
		long otherId = req.getParticipantId();
		if (otherId == currentUserId) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể tạo hội thoại với chính mình.");
		}
		// B1: Load user kia kèm roles để kiểm tra role hợp lệ
		User other = userRepository.findActiveByIdWithRoles(otherId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (other.getStatus() == null || !"active".equalsIgnoreCase(other.getStatus().trim())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tài khoản đối phương không hoạt động hoặc đã bị khóa.");
		}
		// B2: Load user hiện tại để biết role
		User current = userRepository.findActiveByIdWithRoles(currentUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		String currentRole = primaryRoleLabel(current);
		String otherRole = primaryRoleLabel(other);
		// B3: Customer ↔ NV/QL; hoặc nội bộ khi có ít nhất một BranchManager (QL chat liên chi nhánh / NV↔QL).
		boolean customerStaffPair =
				("Customer".equals(currentRole) && ("SalesStaff".equals(otherRole) || "BranchManager".equals(otherRole)))
						|| (("SalesStaff".equals(currentRole) || "BranchManager".equals(currentRole))
								&& "Customer".equals(otherRole));
		boolean staffInternalWithManager =
				("SalesStaff".equals(currentRole) || "BranchManager".equals(currentRole))
						&& ("SalesStaff".equals(otherRole) || "BranchManager".equals(otherRole))
						&& ("BranchManager".equals(currentRole) || "BranchManager".equals(otherRole));
		if (!customerStaffPair && !staffInternalWithManager) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Bạn không thể tạo hội thoại với người dùng này.");
		}
		var existing = chatConversationRepository.findDirectBetweenTwoUsers(currentUserId, otherId);
		if (existing.isPresent()) {
			long cid = existing.get().getId();
			if (req.getInitialMessage() != null && !req.getInitialMessage().isBlank()) {
				persistMessage(currentUserId, cid, req.getInitialMessage().trim(), "text");
			}
			return new CreateChatConversationResponse(cid);
		}
		ChatConversation c = new ChatConversation();
		c = chatConversationRepository.save(c);
		long cid = c.getId();
		ChatParticipant a = new ChatParticipant();
		a.setConversationId(cid);
		a.setUserId(currentUserId);
		a.setUnreadCount(0);
		ChatParticipant b = new ChatParticipant();
		b.setConversationId(cid);
		b.setUserId(otherId);
		b.setUnreadCount(0);
		chatParticipantRepository.save(a);
		chatParticipantRepository.save(b);
		if (req.getInitialMessage() != null && !req.getInitialMessage().isBlank()) {
			persistMessage(currentUserId, cid, req.getInitialMessage().trim(), "text");
		}
		return new CreateChatConversationResponse(cid);
	}

	@Transactional
	public Page<ChatMessageRowDto> listMessages(long currentUserId, long conversationId, int page, int size) {
		assertParticipant(conversationId, currentUserId);
		ChatParticipant mine = chatParticipantRepository.findByConversationIdAndUserId(conversationId, currentUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ACCESS_DENIED));
		// B1: Reset số tin chưa đọc của người xem
		mine.setUnreadCount(0);
		chatParticipantRepository.save(mine);
		// B2: Đánh dấu các message của đối phương là đã đọc
		chatMessageRepository.markReadForConversationAndNotSender(conversationId, currentUserId);
		// Pageable không Sort: repo method đã OrderBySentAtDesc — thêm Sort trùng cột gây lỗi 169 trên SQL Server.
		var pr = PageRequest.of(page, size);
		return chatMessageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pr).map(ChatService::toMessageDto);
	}

	private static ChatMessageRowDto toMessageDto(ChatMessage m) {
		return ChatMessageRowDto.builder()
				.id(m.getId())
				.senderId(m.getSender().getId())
				.senderName(m.getSender().getName())
				.content(m.getContent())
				.sentAt(m.getSentAt())
				.read(m.isRead())
				.build();
	}

	@Transactional
	public SendChatMessageResponse sendMessage(long currentUserId, SendChatMessageRequest req) {
		long cid = req.getConversationId();
		assertParticipant(cid, currentUserId);
		String type = req.getMessageType() == null || req.getMessageType().isBlank() ? "text" : req.getMessageType().trim();
		String text = req.getContent().trim();
		if (text.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Nội dung tin nhắn không được để trống.");
		}
		long mid = persistMessage(currentUserId, cid, text, type);
		return new SendChatMessageResponse(mid);
	}

	private long persistMessage(long senderUserId, long conversationId, String content, String messageType) {
		User sender = userRepository.findByIdAndDeletedFalse(senderUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		ChatMessage m = new ChatMessage();
		m.setConversationId(conversationId);
		m.setSender(sender);
		m.setContent(content);
		m.setMessageType(messageType);
		m.setRead(false);
		m = chatMessageRepository.save(m);
		ChatConversation conv = chatConversationRepository.findById(conversationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_NOT_FOUND));
		conv.setLastMessageAt(Instant.now());
		chatConversationRepository.save(conv);
		for (ChatParticipant p : chatParticipantRepository.findByConversationId(conversationId)) {
			if (!p.getUserId().equals(senderUserId)) {
				p.setUnreadCount(p.getUnreadCount() + 1);
				chatParticipantRepository.save(p);
			}
		}
		return m.getId();
	}

	private void assertParticipant(long conversationId, long userId) {
		chatParticipantRepository.findByConversationIdAndUserId(conversationId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ACCESS_DENIED, "Bạn không tham gia cuộc trò chuyện này."));
	}

	/**
	 * Danh sách người nhận chuyển giao — chỉ quản lý chi nhánh (role BranchManager) thực hiện được:
	 * tư vấn viên (SalesStaff) cùng chi nhánh, hoặc quản lý chi nhánh khác. Không có NV → QL.
	 */
	@Transactional(readOnly = true)
	public List<ChatTransferCandidateDto> listTransferCandidates(long actorUserId, long conversationId) {
		assertParticipant(conversationId, actorUserId);
		User actor = userRepository.findActiveByIdWithRoles(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		String actorRole = primaryRoleLabel(actor);
		if (!"BranchManager".equals(actorRole)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ quản lý chi nhánh mới có thể chuyển giao hội thoại cho đồng nghiệp.");
		}
		validateStaffCustomerThread(conversationId, actorUserId, actor);
		int branchId = staffService.getManagerBranchId(actorUserId);
		return buildTransferCandidatesForBranchManager(actorUserId, branchId);
	}

	private void validateStaffCustomerThread(long conversationId, long actorUserId, User actor) {
		if ("Customer".equals(primaryRoleLabel(actor))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Khách hàng không thể chuyển giao hội thoại.");
		}
		List<ChatParticipant> parts = chatParticipantRepository.findByConversationId(conversationId);
		if (parts.size() != 2) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Hội thoại không hợp lệ.");
		}
		long otherId = parts.stream().map(ChatParticipant::getUserId).filter(id -> !id.equals(actorUserId)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ACCESS_DENIED));
		User other = userRepository.findActiveByIdWithRoles(otherId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (!"Customer".equals(primaryRoleLabel(other))) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ áp dụng cho cuộc trò chuyện với khách hàng.");
		}
	}

	private List<ChatTransferCandidateDto> buildTransferCandidatesForBranchManager(long managerUserId, int branchId) {
		Set<Long> seen = new HashSet<>();
		List<ChatTransferCandidateDto> out = new ArrayList<>();
		for (BranchTeamMemberDto m : branchService.listPublicTeam(branchId)) {
			if (m.getUserId() == null || m.getUserId().equals(managerUserId)) {
				continue;
			}
			User u = userRepository.findActiveByIdWithRoles(m.getUserId()).orElse(null);
			if (u == null) {
				continue;
			}
			if (!"SalesStaff".equals(primaryRoleLabel(u))) {
				continue;
			}
			if (seen.add(m.getUserId())) {
				out.add(new ChatTransferCandidateDto(m.getUserId(), m.getName(),
						m.getRole() != null ? m.getRole() : "Tư vấn viên",
						ChatTransferCandidateDto.GROUP_SAME_BRANCH_SALES));
			}
		}
		for (BranchTeamMemberDto m : branchService.listManagersOfOtherBranches(branchId)) {
			if (m.getUserId() == null || m.getUserId().equals(managerUserId) || !seen.add(m.getUserId())) {
				continue;
			}
			out.add(new ChatTransferCandidateDto(m.getUserId(), m.getName(),
					m.getRole() != null ? m.getRole() : "Quản lý chi nhánh",
					ChatTransferCandidateDto.GROUP_OTHER_BRANCH_MANAGER));
		}
		out.sort(Comparator.comparing(ChatTransferCandidateDto::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	@Transactional
	public void transferConversationToColleague(long actorUserId, long conversationId,
			TransferChatConversationRequest req) {
		long targetId = req.getTargetUserId();
		assertParticipant(conversationId, actorUserId);
		User actor = userRepository.findActiveByIdWithRoles(actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		String actorRole = primaryRoleLabel(actor);
		if (!"BranchManager".equals(actorRole)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Chỉ quản lý chi nhánh mới có thể chuyển giao hội thoại cho đồng nghiệp.");
		}
		validateStaffCustomerThread(conversationId, actorUserId, actor);
		if (targetId == actorUserId) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không thể chuyển cho chính mình.");
		}
		int branchId = staffService.getManagerBranchId(actorUserId);
		boolean allowed = buildTransferCandidatesForBranchManager(actorUserId, branchId).stream()
				.anyMatch(c -> c.userId() == targetId);
		if (!allowed) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Người nhận không nằm trong danh sách được phép.");
		}
		User target = userRepository.findActiveByIdWithRoles(targetId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		String tr = primaryRoleLabel(target);
		if (!"SalesStaff".equals(tr) && !"BranchManager".equals(tr)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chỉ có thể chuyển cho tư vấn viên hoặc quản lý chi nhánh.");
		}
		persistMessage(actorUserId, conversationId, "Đã chuyển cuộc trò chuyện cho " + target.getName() + ".", "text");
		ChatParticipant actorRow = chatParticipantRepository.findByConversationIdAndUserId(conversationId, actorUserId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ACCESS_DENIED));
		chatParticipantRepository.delete(actorRow);
		if (chatParticipantRepository.findByConversationIdAndUserId(conversationId, targetId).isPresent()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Người nhận đã tham gia hội thoại.");
		}
		ChatParticipant np = new ChatParticipant();
		np.setConversationId(conversationId);
		np.setUserId(targetId);
		// persistMessage đã chạy trước khi thêm participant — người nhận chưa được +unread; 1 = tin chuyển giao chưa đọc.
		np.setUnreadCount(1);
		chatParticipantRepository.save(np);
	}
}
