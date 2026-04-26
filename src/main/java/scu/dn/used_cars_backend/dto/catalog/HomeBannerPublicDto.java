package scu.dn.used_cars_backend.dto.catalog;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HomeBannerPublicDto {
	long id;
	String imageUrl;
	int sortOrder;
}
