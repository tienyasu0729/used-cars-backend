package scu.dn.used_cars_backend.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentSessionResponse {

	private String sessionId;
	private String purpose;
	private String status;
	private String qrUrl;
	private String fileUrl;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant expiresAt;
}
