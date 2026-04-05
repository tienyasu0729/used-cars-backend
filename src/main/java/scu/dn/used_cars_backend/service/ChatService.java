package scu.dn.used_cars_backend.service;

// Chat 1-1: hội thoại, tin nhắn, đếm chưa đọc (Sprint 9). WebSocket ghi chú hoãn — FE polling.
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import scu.dn.used_cars_backend.common.exception.BusinessException;
import scu.dn.used_cars_backend.common.exception.ErrorCode;
import scu.dn.used_cars_backend.dto.chat.ChatConversationRowDto;
import scu.dn.used_cars_backend.dto.chat.ChatMessageRowDto;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationRequest;
import scu.dn.used_cars_backend.dto.chat.CreateChatConversationResponse;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageRequest;
import scu.dn.used_cars_backend.dto.chat.SendChatMessageResponse;
import scu.dn.used_cars_backend.entity.ChatConversation;
import scu.dn.used_cars_backend.entity.ChatMessage;
import scu.dn.used_cars_backend.entity.ChatParticipant;
import scu.dn.used_cars_backend.entity.User;
import scu.dn.used_cars_backend.repository.ChatConversationRepository;
import scu.dn.used_cars_backend.repository.ChatMessageRepository;
import scu.dn.used_cars_backend.repository.ChatParticipantRepository;
import scu.dn.used_cars_backend.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

	private final ChatConversationRepository chatConversationRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;

	@Transactional(readOnly = true)
	public List<ChatConversationRowDto> listConversations(long currentUserId) {
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
			rows.add(ChatConversationRowDto.builder()
					.id(cid)
					.participantName(other.getName())
					.participantRole(primaryRoleLabel(other))
					.lastMessage(lastText)
					.lastMessageAt(conv.getLastMessageAt())
					.unreadCount(row.getUnreadCount())
					.build());
		}
		rows.sort(Comparator.comparing(ChatConversationRowDto::getLastMessageAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.reversed());
		return rows;
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
		userRepository.findByIdAndDeletedFalse(otherId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
		mine.setUnreadCount(0);
		chatParticipantRepository.save(mine);
		var pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
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
}
