package scu.dn.used_cars_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import scu.dn.used_cars_backend.entity.ChatConversation;

import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

	@Query("""
			select c from ChatConversation c
			where (select count(p) from ChatParticipant p where p.conversationId = c.id) = 2
			and exists (select 1 from ChatParticipant p1 where p1.conversationId = c.id and p1.userId = :u1)
			and exists (select 1 from ChatParticipant p2 where p2.conversationId = c.id and p2.userId = :u2)
			""")
	Optional<ChatConversation> findDirectBetweenTwoUsers(@Param("u1") long u1, @Param("u2") long u2);
}
