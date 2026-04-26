package scu.dn.used_cars_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.ChatMessage;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@EntityGraph(attributePaths = "sender")
	Page<ChatMessage> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

	Optional<ChatMessage> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

	// Đánh dấu các tin nhắn của đối phương là đã đọc khi người dùng mở hội thoại
	@Modifying
	@Query("UPDATE ChatMessage m SET m.read = true WHERE m.conversationId = :convId AND m.sender.id <> :userId AND m.read = false")
	void markReadForConversationAndNotSender(@Param("convId") long convId, @Param("userId") long userId);
}
