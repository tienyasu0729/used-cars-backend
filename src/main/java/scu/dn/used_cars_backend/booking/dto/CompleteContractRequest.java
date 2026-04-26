package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteContractRequest {

	@NotNull
	private Boolean agreed;

	@NotBlank
	private String signatureType;

	@NotBlank
	private String signatureUrl;

	private String idCardUrl;

	private String licenseUrl;
}
