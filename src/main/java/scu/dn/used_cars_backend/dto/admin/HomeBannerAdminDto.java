package scu.dn.used_cars_backend.dto.admin;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class HomeBannerAdminDto {
	long id;
	String imageUrl;
	String cloudinaryPublicId;
	int sortOrder;
	Instant createdAt;
}
