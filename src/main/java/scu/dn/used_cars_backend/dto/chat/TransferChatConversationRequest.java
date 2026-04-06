package scu.dn.used_cars_backend.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferChatConversationRequest {

	@NotNull
	private Long targetUserId;
}
