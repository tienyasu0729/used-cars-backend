package scu.dn.used_cars_backend.sms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsPendingResponse {

    private Long id;

    private String phone;

    private String content;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
