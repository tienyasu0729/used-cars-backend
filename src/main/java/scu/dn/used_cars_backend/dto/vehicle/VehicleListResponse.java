package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleListResponse {

	private List<VehicleSummaryDto> items;
	private PageMetaDto meta;

}
