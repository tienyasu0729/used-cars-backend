package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;
import scu.dn.used_cars_backend.dto.vehicle.PageMetaDto;

import java.util.List;

@Value
@Builder
public class AdminCatalogModelsPageDto {
	List<AdminCatalogModelRowDto> content;
	PageMetaDto meta;
}
