package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleImageDto {

	private Long id;
	private String url;
	private int sortOrder;
	private boolean primaryImage;

}
