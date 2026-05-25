package scu.dn.used_cars_backend.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

	@NotBlank
	@Pattern(regexp = "^0\\d{9}$")
	private String phone;

	@NotBlank
	@Pattern(regexp = "^\\d{6}$")
	private String otpCode;
}
