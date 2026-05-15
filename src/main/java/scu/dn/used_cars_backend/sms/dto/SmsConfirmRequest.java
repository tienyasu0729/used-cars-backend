package scu.dn.used_cars_backend.sms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsConfirmRequest {

    @NotNull
    private Long id;

    @NotNull
    @Pattern(regexp = "^(SENT|FAILED)$")
    private String status;
}
