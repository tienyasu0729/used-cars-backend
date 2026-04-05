package scu.dn.used_cars_backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendChatMessageRequest {

	@NotNull
	private Long conversationId;

	@NotBlank
	private String content;

	private String messageType;
}
