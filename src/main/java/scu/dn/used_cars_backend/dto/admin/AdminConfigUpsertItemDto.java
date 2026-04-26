package scu.dn.used_cars_backend.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminConfigUpsertItemDto {

	@NotBlank
	@JsonProperty("key")
	private String key;

	private String value;
}
