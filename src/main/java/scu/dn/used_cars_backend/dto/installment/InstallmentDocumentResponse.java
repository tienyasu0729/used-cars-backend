package scu.dn.used_cars_backend.dto.installment;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class InstallmentDocumentResponse {
	private Long id;
	private String documentType;
	private String documentUrl;
	private String originalFileName;
	private Instant uploadedAt;
}
