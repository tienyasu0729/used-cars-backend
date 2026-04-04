package scu.dn.used_cars_backend.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

	private Long id;

	private Long customerId;

	private String customerName;

	private String customerPhone;

	private Long vehicleId;

	private String vehicleTitle;

	private String vehicleListingId;

	private Integer branchId;

	private String branchName;

	@JsonFormat(pattern = "yyyy-MM-dd")
	private LocalDate bookingDate;

	@JsonFormat(pattern = "HH:mm")
	private LocalTime timeSlot;

	private Long staffId;

	private String status;

	private String note;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private Instant createdAt;

	private List<BookingStatusHistoryItemDto> statusHistory;
}
