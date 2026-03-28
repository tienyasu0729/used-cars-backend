package scu.dn.used_cars_backend.dto.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogCategoryDto {

	private Integer id;
	private String name;
	private String status;

}
