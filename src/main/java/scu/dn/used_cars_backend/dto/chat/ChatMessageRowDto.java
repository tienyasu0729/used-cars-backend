package scu.dn.used_cars_backend.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRowDto {

	private long id;
	private long senderId;
	private String senderName;
	private String content;
	private Instant sentAt;

	@JsonProperty("isRead")
	private boolean read;
}
