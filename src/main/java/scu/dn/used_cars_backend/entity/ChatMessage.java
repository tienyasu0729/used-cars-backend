package scu.dn.used_cars_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Tin nhắn chat — map ChatMessages (Sprint 9).
@Getter
@Setter
@Entity
@Table(name = "ChatMessages")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "conversation_id", nullable = false)
	private Long conversationId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id", nullable = false)
	private User sender;

	@Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
	private String content;

	@Column(name = "message_type", nullable = false, length = 20)
	private String messageType = "text";

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@PrePersist
	void onCreate() {
		if (sentAt == null) {
			sentAt = Instant.now();
		}
	}
}
