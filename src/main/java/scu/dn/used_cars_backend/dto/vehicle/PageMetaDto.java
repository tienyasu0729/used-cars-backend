package scu.dn.used_cars_backend.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMetaDto {

	private int page;
	private int size;
	private long totalElements;
	private int totalPages;

}
