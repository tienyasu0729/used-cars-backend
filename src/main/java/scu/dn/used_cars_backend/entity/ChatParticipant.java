package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

// Người tham gia hội thoại — PK (conversation_id, user_id).
@Getter
@Setter
@Entity
@Table(name = "ChatParticipants")
@IdClass(ChatParticipant.ChatParticipantKey.class)
public class ChatParticipant {

	@Id
	@Column(name = "conversation_id")
	private Long conversationId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "unread_count", nullable = false)
	private int unreadCount;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@PrePersist
	void onCreate() {
		if (joinedAt == null) {
			joinedAt = Instant.now();
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ChatParticipantKey implements Serializable {
		private Long conversationId;
		private Long userId;
	}
}
