package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.ChatMessage;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@EntityGraph(attributePaths = "sender")
	Page<ChatMessage> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

	Optional<ChatMessage> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);
}
