package scu.dn.used_cars_backend.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateChatConversationRequest {

	@NotNull
	private Long participantId;

	private String initialMessage;
}
