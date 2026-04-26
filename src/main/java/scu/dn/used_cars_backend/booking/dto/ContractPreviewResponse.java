package scu.dn.used_cars_backend.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
public class ContractPreviewResponse {

	private Long bookingId;
	private String contractStatus;
	private String termsVersion;
	private String termsContent;

	private String customerName;
	private String customerPhone;
	private String customerEmail;

	private String vehicleTitle;
	private String vehicleListingId;
	private String branchName;

	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate bookingDate;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime timeSlot;

	private String signatureUrl;
	private String idCardUrl;
	private String licenseUrl;
	private String contentSha256;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant signedAt;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant expiresAt;
}
