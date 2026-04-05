package scu.dn.used_cars_backend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailDto {

	private TransactionRowDto row;
	private List<TimelineEventDto> timeline;
	private String rawGatewayRef;
	private String notes;
}
