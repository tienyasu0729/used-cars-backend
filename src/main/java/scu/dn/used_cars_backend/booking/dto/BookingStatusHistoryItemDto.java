package scu.dn.used_cars_backend.booking.dto;

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
public class BookingStatusHistoryItemDto {

	private String oldStatus;

	private String newStatus;

	private Long changedBy;

	private String note;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant changedAt;
}
