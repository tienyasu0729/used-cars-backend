package scu.dn.used_cars_backend.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationRowDto {

	private long id;
	private String participantName;
	private String participantRole;
	private String lastMessage;
	private Instant lastMessageAt;
	private int unreadCount;
}
