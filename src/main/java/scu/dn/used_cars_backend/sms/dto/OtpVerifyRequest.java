package scu.dn.used_cars_backend.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerifyRequest {

    @NotBlank
    @Pattern(regexp = "^0\\d{9}$")
    private String phone;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    private String otpCode;

    @NotBlank
    private String referenceType;

    @NotNull
    private Long referenceId;
}
