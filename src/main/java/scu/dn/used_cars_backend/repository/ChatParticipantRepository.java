package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import scu.dn.used_cars_backend.entity.ChatParticipant;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipant.ChatParticipantKey> {

	List<ChatParticipant> findByUserId(Long userId);

	List<ChatParticipant> findByConversationId(Long conversationId);

	Optional<ChatParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
}
