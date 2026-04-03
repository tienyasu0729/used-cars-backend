package scu.dn.used_cars_backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminConfigEntryDto {
	@JsonProperty("key")
	String configKey;
	String value;
	String description;
}
